package com.clipflow.app.player

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager

internal const val AUTH_LOG_TAG = "ClipFlowAuth"
internal const val FEED_LOG_TAG = "ClipFlowFeed"

internal fun authLog(priority: Int, message: String, error: Throwable? = null) {
    runCatching {
        val full = if (error != null) "$message\n${Log.getStackTraceString(error)}" else message
        Log.println(priority, AUTH_LOG_TAG, full)
    }
}

internal fun feedLog(priority: Int, message: String, error: Throwable? = null) {
    runCatching {
        val full = if (error != null) "$message\n${Log.getStackTraceString(error)}" else message
        Log.println(priority, FEED_LOG_TAG, full)
    }
}

/**
 * Durable session persistence backed synchronously by SharedPreferences (`clipflow_auth_prefs`)
 * so signed-in Google/YouTube cookies and profile metadata survive cold restarts and are restored
 * into [CookieManager] on app launch without relying on WebView memory or AndroidKeyStore alone.
 */
object AuthSessionManager {
    const val PREFS_NAME = "clipflow_auth_prefs"
    const val KEY_RAW_COOKIE_STRING = "raw_cookie_string"
    const val KEY_SAPISID = "sapisid"
    const val KEY_SECURE_3PAPISID = "secure_3papisid"
    const val KEY_CLIENT_VERSION = "client_version"
    const val KEY_VISITOR_DATA = "visitor_data"
    const val KEY_PROFILE_NAME = "profile_name"
    const val KEY_PROFILE_HANDLE = "profile_handle"
    const val KEY_PROFILE_AVATAR = "profile_avatar"
    const val KEY_PROFILE_EMAIL = "profile_email"

    internal val YOUTUBE_COOKIE_URLS = listOf(
        "https://youtube.com",
        "https://m.youtube.com",
        "https://www.youtube.com",
    )

    internal val GOOGLE_ACCOUNT_COOKIE_URLS = listOf(
        "https://accounts.google.com",
        "https://www.google.com",
        "https://google.com",
        "https://myaccount.google.com",
    )

    internal val GOOGLE_ONLY_COOKIE_NAMES = setOf(
        "LSID",
        "__Host-1PLSID",
        "__Host-3PLSID",
        "__Host-GAPS",
        "ACCOUNT_CHOOSER",
        "OTZ",
        "OSID",
        "__Secure-OSID",
    )

    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    internal var appContext: Context? = null
        private set

    @Volatile
    private var explicitlySignedOut = false

    /** Optional test hook overriding `CookieManager.getInstance().getCookie(url)`. */
    @Volatile
    internal var cookieHeaderProviderForTest: ((String) -> String?)? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (prefs != null) return
        synchronized(lock) {
            if (prefs == null) {
                prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
        explicitlySignedOut = false
    }

    internal fun isExplicitlySignedOut(): Boolean = explicitlySignedOut

    internal fun clearSignOutGuardForTest() {
        explicitlySignedOut = false
    }

    fun saveSession(rawCookieHeader: String) {
        val parsed = parseCookieHeader(rawCookieHeader)
        val map = if (hasGenuineYouTubeSession(parsed)) stripGoogleOnlyAndTransientCookies(parsed) else parsed
        val normalized = formatCookieHeader(map)
        val sapisid = map["SAPISID"]?.takeIf { it.isNotBlank() }
        val secure3p = map["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() }
        explicitlySignedOut = false
        synchronized(lock) {
            prefs?.edit()?.apply {
                putString(KEY_RAW_COOKIE_STRING, normalized)
                if (sapisid != null) putString(KEY_SAPISID, sapisid) else remove(KEY_SAPISID)
                if (secure3p != null) putString(KEY_SECURE_3PAPISID, secure3p) else remove(KEY_SECURE_3PAPISID)
            }?.commit()
        }
        val logMsg = "Saved session cookies (count=${map.size}, hasLoginInfo=${!map["LOGIN_INFO"].isNullOrBlank()}, hasSAPISID=${sapisid != null}, has3PAPISID=${secure3p != null})"
        authLog(Log.INFO, logMsg)
        feedLog(Log.INFO, logMsg)
    }

    fun updateCookies(cookieMap: Map<String, String>) {
        if (cookieMap.isEmpty()) return
        saveSession(formatCookieHeader(cookieMap))
    }

    fun saveProfile(profile: Profile) {
        synchronized(lock) {
            prefs?.edit()?.apply {
                putString(KEY_PROFILE_NAME, profile.name)
                if (profile.handle != null) putString(KEY_PROFILE_HANDLE, profile.handle) else remove(KEY_PROFILE_HANDLE)
                if (profile.avatar != null) putString(KEY_PROFILE_AVATAR, profile.avatar) else remove(KEY_PROFILE_AVATAR)
                if (profile.email != null) putString(KEY_PROFILE_EMAIL, profile.email) else remove(KEY_PROFILE_EMAIL)
            }?.commit()
        }
    }

    fun loadProfile(): Profile? {
        val p = prefs ?: return null
        val name = p.getString(KEY_PROFILE_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        return Profile(
            name = name,
            handle = p.getString(KEY_PROFILE_HANDLE, null),
            avatar = p.getString(KEY_PROFILE_AVATAR, null),
            email = p.getString(KEY_PROFILE_EMAIL, null),
        )
    }

    fun saveClientConfig(clientVersion: String, visitorData: String?) {
        synchronized(lock) {
            prefs?.edit()?.apply {
                putString(KEY_CLIENT_VERSION, clientVersion)
                if (visitorData != null) putString(KEY_VISITOR_DATA, visitorData) else remove(KEY_VISITOR_DATA)
            }?.commit()
        }
    }

    fun getClientVersion(): String? = prefs?.getString(KEY_CLIENT_VERSION, null)?.takeIf { it.isNotBlank() }

    fun getVisitorData(): String? = prefs?.getString(KEY_VISITOR_DATA, null)?.takeIf { it.isNotBlank() }

    fun getRawCookieString(): String? = prefs?.getString(KEY_RAW_COOKIE_STRING, null)?.takeIf { it.isNotBlank() }

    fun getSapisid(): String? =
        prefs?.getString(KEY_SAPISID, null)?.takeIf { it.isNotBlank() }
            ?: getRawCookieString()?.let { parseCookieHeader(it)["SAPISID"]?.takeIf { v -> v.isNotBlank() } }

    fun getSecure3pApiSid(): String? =
        prefs?.getString(KEY_SECURE_3PAPISID, null)?.takeIf { it.isNotBlank() }
            ?: getRawCookieString()?.let { parseCookieHeader(it)["__Secure-3PAPISID"]?.takeIf { v -> v.isNotBlank() } }

    fun getEffectiveSapisid(): String? =
        getSapisid()
            ?: getSecure3pApiSid()
            ?: getRawCookieString()?.let { header ->
                val map = parseCookieHeader(header)
                map["__Secure-1PAPISID"]?.takeIf { it.isNotBlank() } ?: map["APISID"]?.takeIf { it.isNotBlank() }
            }

    fun hasValidSession(): Boolean {
        val raw = getRawCookieString() ?: return false
        return hasAuthCookiePair(parseCookieHeader(raw))
    }

    private fun readCookiesFromUrls(urls: List<String>): LinkedHashMap<String, String> {
        val out = linkedMapOf<String, String>()
        if (explicitlySignedOut) return out
        val testProvider = cookieHeaderProviderForTest
        if (testProvider != null) {
            for (url in urls) {
                val raw = runCatching { testProvider(url) }.getOrNull().orEmpty()
                if (raw.isNotBlank()) out.putAll(parseCookieHeader(raw))
            }
            return out
        }
        runCatching {
            val cm = CookieManager.getInstance()
            for (url in urls) {
                val raw = cm.getCookie(url).orEmpty()
                if (raw.isNotBlank()) out.putAll(parseCookieHeader(raw))
            }
        }
        return out
    }

    /**
     * Reads `.youtube.com` cookies dynamically from Android's [CookieManager] (or test hook)
     * across `youtube.com`, `m.youtube.com`, and `www.youtube.com`.
     */
    internal fun readYouTubeCookiesFromManager(): LinkedHashMap<String, String> =
        readCookiesFromUrls(YOUTUBE_COOKIE_URLS)

    /**
     * Reads Google Account cookies dynamically from Android's [CookieManager] for
     * `accounts.google.com` / `.google.com` when passive YouTube sign-in exchange is needed.
     */
    internal fun readGoogleAccountCookiesFromManager(): LinkedHashMap<String, String> =
        readCookiesFromUrls(GOOGLE_ACCOUNT_COOKIE_URLS)

    /**
     * Clears only `.youtube.com` / `www.youtube.com` / `m.youtube.com` cookies in [CookieManager]
     * while leaving `accounts.google.com` and `.google.com` login cookies intact so passive
     * session exchange can mint fresh `.youtube.com` session cookies.
     */
    internal fun clearYouTubeCookiesInManager() {
        runCatching {
            val cm = CookieManager.getInstance()
            val names = LinkedHashSet<String>()
            for (url in YOUTUBE_COOKIE_URLS) {
                val raw = cm.getCookie(url).orEmpty()
                parseCookieHeader(raw).keys.forEach { names += it }
            }
            getRawCookieString()?.let { raw ->
                parseCookieHeader(raw).keys.forEach { names += it }
            }
            names += GOOGLE_ONLY_COOKIE_NAMES
            names += listOf(
                "SID", "HSID", "SSID", "APISID", "SAPISID",
                "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID", "__Secure-3PAPISID",
                "__Secure-1PSIDTS", "__Secure-3PSIDTS", "SIDCC", "__Secure-1PSIDCC", "__Secure-3PSIDCC",
                "LOGIN_INFO",
            )
            for (name in names) {
                if (name.isBlank()) continue
                for (url in YOUTUBE_COOKIE_URLS) {
                    cm.setCookie(url, "$name=; Max-Age=0; Path=/; Secure")
                    if (!name.startsWith("__Host-", ignoreCase = true)) {
                        cm.setCookie(url, "$name=; Domain=.youtube.com; Max-Age=0; Path=/; Secure")
                        cm.setCookie(url, "$name=; Domain=youtube.com; Max-Age=0; Path=/; Secure")
                    }
                }
            }
            cm.flush()
            feedLog(Log.INFO, "Cleared ${names.size} YouTube domain cookie keys in CookieManager (preserving Google Account cookies)")
        }.onFailure { err ->
            feedLog(Log.WARN, "Failed clearing YouTube cookies in CookieManager", err)
        }
    }

    /**
     * Restores persisted cookies from `clipflow_auth_prefs` back into Android's [CookieManager]
     * on app startup so WebView and browser-dependent components stay signed in across launches.
     *
     * If the persisted cookies are an incomplete/poisoned capture (missing `LOGIN_INFO` with
     * Google-only cookies such as `LSID` or unexchanged `YTSESSION-*`), cleans poisoned cookies
     * from `.youtube.com` in [CookieManager] instead of writing Google-only cookies there.
     */
    fun restoreCookiesToCookieManager(): Boolean {
        val raw = getRawCookieString() ?: return false
        val map = parseCookieHeader(raw)
        if (!hasAuthCookiePair(map)) return false
        if (isPoisonedGoogleOnlyCookieMap(map)) {
            feedLog(
                Log.WARN,
                "restoreCookiesToCookieManager: incomplete session (hasLoginInfo=${!map["LOGIN_INFO"].isNullOrBlank()}, keys=${map.keys.joinToString(",")}); cleaning .youtube.com cookies pending exchange",
            )
            clearYouTubeCookiesInManager()
            return false
        }
        val cleanMap = stripGoogleOnlyAndTransientCookies(map)
        if (cleanMap.isEmpty()) return false
        return writeYouTubeCookiesToManager(cleanMap, logRestore = true)
    }

    internal fun syncCookiesToManager(cookies: Map<String, String>) {
        if (explicitlySignedOut || cookies.isEmpty()) return
        val cleanMap = stripGoogleOnlyAndTransientCookies(cookies)
        if (cleanMap.isEmpty()) return
        writeYouTubeCookiesToManager(cleanMap, logRestore = false)
    }

    private fun writeYouTubeCookiesToManager(cookies: Map<String, String>, logRestore: Boolean): Boolean =
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val entries = buildCookieRestoreEntries(cookies)
            for ((url, cookieLine) in entries) {
                cm.setCookie(url, cookieLine)
            }
            cm.flush()
            if (logRestore) authLog(Log.INFO, "Restored ${cookies.size} session cookies into CookieManager")
            true
        }.getOrElse { err ->
            if (logRestore) authLog(Log.WARN, "Failed restoring cookies into CookieManager", err)
            false
        }

    fun clear() {
        explicitlySignedOut = true
        synchronized(lock) {
            prefs?.edit()?.clear()?.commit()
        }
        runCatching {
            CookieManager.getInstance().apply {
                removeSessionCookies(null)
                removeAllCookies(null)
                flush()
            }
        }
    }

    internal fun isExpiredOrDeletedCookieValue(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        val trimmed = value.trim()
        return trimmed == "\"\"" ||
            trimmed.equals("EXPIRED", ignoreCase = true) ||
            trimmed.equals("deleted", ignoreCase = true)
    }

    internal fun isGoogleOnlyOrTransientCookieName(name: String): Boolean =
        name in GOOGLE_ONLY_COOKIE_NAMES || name.startsWith("YTSESSION-", ignoreCase = true)

    internal fun parseCookieHeader(header: String?): LinkedHashMap<String, String> {
        val out = linkedMapOf<String, String>()
        if (header.isNullOrBlank()) return out
        header.split(';').forEach { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@forEach
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (name.isNotEmpty() && !isExpiredOrDeletedCookieValue(value)) {
                out[name] = value
            }
        }
        return out
    }

    internal fun stripGoogleOnlyAndTransientCookies(map: Map<String, String>): LinkedHashMap<String, String> {
        val out = linkedMapOf<String, String>()
        for ((name, value) in map) {
            if (name.isBlank() || isExpiredOrDeletedCookieValue(value)) continue
            if (isGoogleOnlyOrTransientCookieName(name)) continue
            out[name] = value
        }
        return out
    }

    internal fun formatCookieHeader(map: Map<String, String>): String =
        map.entries.filter { it.key.isNotBlank() && !isExpiredOrDeletedCookieValue(it.value) }
            .joinToString("; ") { "${it.key}=${it.value}" }

    internal fun hasAuthCookiePair(map: Map<String, String>): Boolean {
        val hasApiSid = !map["SAPISID"].isNullOrBlank() ||
            !map["__Secure-3PAPISID"].isNullOrBlank() ||
            !map["__Secure-1PAPISID"].isNullOrBlank() ||
            !map["APISID"].isNullOrBlank()
        val hasSessionId = !map["SID"].isNullOrBlank() ||
            !map["HSID"].isNullOrBlank() ||
            !map["SSID"].isNullOrBlank() ||
            !map["__Secure-1PSID"].isNullOrBlank() ||
            !map["__Secure-3PSID"].isNullOrBlank() ||
            !map["LOGIN_INFO"].isNullOrBlank()
        return hasApiSid && hasSessionId
    }

    /**
     * Detects when `SignInScreen` captured `accounts.google.com` / `myaccount.google.com` cookies
     * before YouTube's `/signin?action_handle_signin=true` handoff minted `LOGIN_INFO` on `.youtube.com`.
     */
    internal fun isPoisonedGoogleOnlyCookieMap(map: Map<String, String>): Boolean {
        if (!map["LOGIN_INFO"].isNullOrBlank()) return false
        return map.keys.any(::isGoogleOnlyOrTransientCookieName)
    }

    /**
     * True when [map] represents a genuine `.youtube.com` session (contains `LOGIN_INFO` +
     * `SAPISID`/`__Secure-3PAPISID` + session ID, or auth pair without incomplete Google-only markers).
     */
    internal fun hasGenuineYouTubeSession(map: Map<String, String>): Boolean =
        !map["LOGIN_INFO"].isNullOrBlank() && hasAuthCookiePair(map) && !isPoisonedGoogleOnlyCookieMap(map)

    internal fun buildCookieRestoreEntries(cookies: Map<String, String>): List<Pair<String, String>> {
        val entries = ArrayList<Pair<String, String>>(cookies.size * 2)
        for ((name, value) in cookies) {
            if (name.isBlank() || isExpiredOrDeletedCookieValue(value)) continue
            if (name.startsWith("__Host-", ignoreCase = true)) {
                val cookieLine = "$name=$value; Path=/; Secure"
                entries += "https://www.youtube.com" to cookieLine
                entries += "https://m.youtube.com" to cookieLine
            } else {
                val cookieLine = "$name=$value; Domain=.youtube.com; Path=/; Secure"
                entries += "https://www.youtube.com" to cookieLine
                entries += "https://m.youtube.com" to cookieLine
            }
        }
        return entries
    }
}
