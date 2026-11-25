package dev.vtoppp.vtop.data

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

class DnsWithDohFallbackTest {

    @Test
    fun dohResultsAreCachedForFiveMinutes() {
        var currentTime = 1_000_000L
        val callCount = AtomicInteger(0)
        val dummyAddress = InetAddress.getByAddress("dummy.local", byteArrayOf(127, 0, 0, 1))

        val mockDoh = Dns { hostname ->
            callCount.incrementAndGet()
            listOf(dummyAddress)
        }

        val dns = DnsWithDohFallback(
            nowMs = { currentTime },
            dohOverride = mockDoh,
        )

        val hostname = "nonexistent.example.local"

        // 1. Initial lookup queries DoH
        val res1 = dns.lookup(hostname)
        assertEquals(listOf(dummyAddress), res1)
        assertEquals(1, callCount.get())

        // 2. Lookup within 5 minutes returns cached result without invoking DoH
        currentTime += 4 * 60 * 1000L // +4 minutes
        val res2 = dns.lookup(hostname)
        assertEquals(listOf(dummyAddress), res2)
        assertEquals(1, callCount.get())

        // 3. Lookup after 5 minutes expires cache and invokes DoH again
        currentTime += 2 * 60 * 1000L // +2 minutes (total 6 minutes elapsed)
        val res3 = dns.lookup(hostname)
        assertEquals(listOf(dummyAddress), res3)
        assertEquals(2, callCount.get())
    }
}
