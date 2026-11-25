package dev.vtoppp.vtop.data

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * System DNS answers used verbatim (NAT64 synthetic addresses included —
 * 464XLAT routes them, while raw IPv4 can be unroutable on v6-only carriers);
 * DoH (1.1.1.1) only when the system resolver yields nothing.
 */
class DnsWithDohFallback(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    dohOverride: Dns? = null,
) : Dns {

    private val doh: Dns = dohOverride ?: DnsOverHttps.Builder()
        .client(
            // Share root ConnectionPool/Dispatcher while keeping system DNS for DoH bootstrap IPs.
            sharedRootHttpClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
        )
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
        )
        .build()

    private val dohCache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    override fun lookup(hostname: String): List<InetAddress> {
        val sys = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            AppLog.d("dns sysfail $hostname: ${e.message}")
            emptyList()
        }
        if (sys.isNotEmpty()) return sys

        val now = nowMs()
        val cached = dohCache[hostname]
        if (cached != null && now < cached.second) {
            return cached.first
        }

        val dohResult = try {
            doh.lookup(hostname)
        } catch (e2: Exception) {
            AppLog.d("dns dohfail $hostname: ${e2.javaClass.simpleName} ${e2.message}")
            throw UnknownHostException("system and DoH both failed for $hostname")
        }
        if (dohResult.isEmpty()) {
            throw UnknownHostException("no usable addresses for $hostname")
        }
        dohCache[hostname] = dohResult to (now + TTL_MS)
        // A-only DoH answers on v6-only networks still ride 464XLAT via clat.
        if (dohResult.all { it.address.size == 4 }) {
            AppLog.d("dns doh v4 fallback for $hostname (${dohResult.size})")
        }
        return dohResult
    }

    companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}

/**
 * App-wide single [DnsWithDohFallback]: OkHttp per-client DNS instances each
 * build their own DoH connection pool, so both the portal transport
 * ([OkHttpHttpClient]) and the captcha client share this lazily-created one.
 * Constructed on first use; [DnsOverHttps] bootstrap hosts are numeric
 * literals, so init never touches real DNS.
 */
val sharedDnsWithDohFallback: DnsWithDohFallback by lazy { DnsWithDohFallback() }
