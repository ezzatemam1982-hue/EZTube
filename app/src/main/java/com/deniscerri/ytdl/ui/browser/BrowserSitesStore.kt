package com.deniscerri.ytdl.ui.browser

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class BrowserSite(
    val url: String,
    val title: String,
    val isSaved: Boolean,
    val updatedAt: Long,
)

/**
 * Persists Saved (bookmarked) and Recent websites for the Downloader in-app browser
 * inside dedicated [SharedPreferences] using JSON serialization.
 */
object BrowserSitesStore {

    private const val PREFS_NAME = "in_app_browser_sites"
    private const val KEY_SAVED = "saved_sites_json"
    private const val KEY_RECENT = "recent_sites_json"
    private const val MAX_SAVED = 50
    private const val MAX_RECENT = 15

    fun getSavedAndRecentSites(context: Context): List<BrowserSite> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = readList(prefs.getString(KEY_SAVED, null), isSaved = true)
            .sortedByDescending { it.updatedAt }
        val savedKeys = saved.mapTo(HashSet()) { canonicalKey(it.url) }

        val recent = readList(prefs.getString(KEY_RECENT, null), isSaved = false)
            .filter { canonicalKey(it.url) !in savedKeys }
            .sortedByDescending { it.updatedAt }

        return saved + recent
    }

    fun hasRecentSites(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readList(prefs.getString(KEY_RECENT, null), isSaved = false).isNotEmpty()
    }

    fun isSaved(context: Context, url: String?): Boolean {
        val normalized = normalizeWebUrl(url) ?: return false
        val key = canonicalKey(normalized)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readList(prefs.getString(KEY_SAVED, null), isSaved = true)
            .any { canonicalKey(it.url) == key }
    }

    /**
     * Toggles [url] in Saved Sites. Returns `true` if saved, `false` if removed.
     */
    fun toggleSaved(context: Context, url: String?, title: String?): Boolean {
        val normalized = normalizeWebUrl(url) ?: return false
        val key = canonicalKey(normalized)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = readList(prefs.getString(KEY_SAVED, null), isSaved = true).toMutableList()
        val existingIndex = saved.indexOfFirst { canonicalKey(it.url) == key }
        return if (existingIndex >= 0) {
            saved.removeAt(existingIndex)
            writeList(prefs, KEY_SAVED, saved)
            false
        } else {
            val resolvedTitle = resolveTitle(normalized, title)
            saved.add(
                0,
                BrowserSite(
                    url = normalized,
                    title = resolvedTitle,
                    isSaved = true,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            writeList(prefs, KEY_SAVED, saved.take(MAX_SAVED))
            true
        }
    }

    fun recordRecent(context: Context, url: String?, title: String?) {
        val incognito = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("incognito", false)
        if (incognito) return

        val normalized = normalizeWebUrl(url) ?: return
        val key = canonicalKey(normalized)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recent = readList(prefs.getString(KEY_RECENT, null), isSaved = false).toMutableList()
        val existing = recent.firstOrNull { canonicalKey(it.url) == key }
        recent.removeAll { canonicalKey(it.url) == key }

        val resolvedTitle = resolveTitle(normalized, title, existing?.title)
        recent.add(
            0,
            BrowserSite(
                url = normalized,
                title = resolvedTitle,
                isSaved = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
        writeList(prefs, KEY_RECENT, recent.take(MAX_RECENT))

        // Refresh title of matching saved bookmark when page finishes loading with a better title.
        val saved = readList(prefs.getString(KEY_SAVED, null), isSaved = true).toMutableList()
        val savedIdx = saved.indexOfFirst { canonicalKey(it.url) == key }
        if (savedIdx >= 0 && !title.isNullOrBlank() && saved[savedIdx].title != resolvedTitle) {
            saved[savedIdx] = saved[savedIdx].copy(title = resolvedTitle)
            writeList(prefs, KEY_SAVED, saved)
        }
    }

    fun removeSite(context: Context, site: BrowserSite) {
        val key = canonicalKey(site.url)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = readList(prefs.getString(KEY_SAVED, null), isSaved = true)
            .filterNot { canonicalKey(it.url) == key }
        val recent = readList(prefs.getString(KEY_RECENT, null), isSaved = false)
            .filterNot { canonicalKey(it.url) == key }
        prefs.edit()
            .putString(KEY_SAVED, encodeList(saved))
            .putString(KEY_RECENT, encodeList(recent))
            .apply()
    }

    fun clearRecent(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECENT).apply()
    }

    fun normalizeInputToUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val looksLikeHost = !trimmed.contains(' ') && trimmed.contains('.')
        return if (looksLikeHost) {
            "https://$trimmed"
        } else {
            Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .path("search")
                .appendQueryParameter("q", trimmed)
                .build()
                .toString()
        }
    }

    private fun normalizeWebUrl(url: String?): String? {
        val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return trimmed
    }

    private fun canonicalKey(url: String): String {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return url.trim()
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val path = uri.encodedPath?.takeIf { it.isNotEmpty() && it != "/" } ?: ""
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        return "$scheme://$host$path$query"
    }

    private fun resolveTitle(url: String, candidate: String?, fallback: String? = null): String {
        val clean = candidate?.trim()?.takeIf {
            it.isNotEmpty() &&
                !it.equals("about:blank", ignoreCase = true) &&
                !it.equals(url, ignoreCase = true)
        }
        if (clean != null) return clean
        val previous = fallback?.trim()?.takeIf { it.isNotEmpty() }
        if (previous != null) return previous
        return runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: url
    }

    private fun readList(rawJson: String?, isSaved: Boolean): List<BrowserSite> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val title = obj.optString("title").ifBlank { url }
                    val updatedAt = obj.optLong("updatedAt", 0L)
                    add(BrowserSite(url = url, title = title, isSaved = isSaved, updatedAt = updatedAt))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeList(
        prefs: android.content.SharedPreferences,
        key: String,
        items: List<BrowserSite>,
    ) {
        prefs.edit().putString(key, encodeList(items)).apply()
    }

    private fun encodeList(items: List<BrowserSite>): String {
        val array = JSONArray()
        items.forEach { site ->
            val obj = JSONObject()
                .put("url", site.url)
                .put("title", site.title)
                .put("updatedAt", site.updatedAt)
            array.put(obj)
        }
        return array.toString()
    }
}
