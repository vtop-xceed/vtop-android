package dev.vtoppp.vtop.data

import android.content.Context
import java.io.File

/**
 * Offline cache of fetched section HTML. Keyed by endpoint slug, one file per
 * endpoint plus an mtime that doubles as the saved-at timestamp. Kept in
 * `filesDir` (not disk-cache) so clear-on-failures never wipes the offline
 * copies silently; everything is user-scoped text and safe to keep.
 */
object SessionCache {

    private var root: File? = null

    /** Newest-wins LRU cap; VTOP traffic is bounded so this rarely engages. */
    private const val MAX_ENTRIES = 24

    fun init(context: Context) {
        init(File(context.filesDir, "section-cache").apply { mkdirs() })
    }

    /** T19 seam: direct root injection for pure-JVM tests. */
    fun init(root: File) {
        this.root = root.also { it.mkdirs() }
    }

    /** Longest-lived edge case: endpoint URLs are ASCII-ish server pages. */
    private fun fileFor(endpoint: String): File? {
        val dir = root ?: return null
        val slug = endpoint.filter { it.isLetterOrDigit() }.take(120)
        if (slug.isEmpty()) return null
        return File(dir, "$slug.html")
    }

    /** Stores the latest copy; failures never throw (cache is best effort). */
    fun save(endpoint: String, html: String) {
        val file = fileFor(endpoint) ?: return
        try {
            file.writeText(html)
            prune()
        } catch (e: Exception) {
            AppLog.d("cache: save $endpoint failed: ${e.message}")
        }
    }

    /** Drops the oldest save-times beyond [MAX_ENTRIES] (mtime doubles as saved-at). */
    private fun prune() {
        val files = root?.listFiles { f -> f.isFile && f.name.endsWith(".html") } ?: return
        files.sortedByDescending { it.lastModified() }
            .drop(MAX_ENTRIES)
            .forEach { it.delete() }
    }

    /** Cached HTML and its saved-at epoch ms, or null when nothing cached. */
    fun load(endpoint: String): Pair<String, Long>? {
        val file = fileFor(endpoint) ?: return null
        return try {
            if (!file.exists()) return null
            val html = file.readText()
            if (html.isBlank()) return null
            val savedAt = file.lastModified()
            if (savedAt <= 0L) null else html to savedAt
        } catch (e: Exception) {
            AppLog.d("cache: load $endpoint failed: ${e.message}")
            null
        }
    }

    /** Human "2h ago" label for the cached badge. */
    fun ageLabel(savedAt: Long): String {
        val mins = (System.currentTimeMillis() - savedAt) / 60_000L
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    }
}
