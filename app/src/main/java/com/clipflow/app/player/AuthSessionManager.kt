package com.clipflow.app.player

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager

internal const val AUTH_LOG_TAG = "ClipFlowAuth"

internal fun authLog(priority: Int, message: String, error: Throwable? = null) {
    runCatching {
        val full = if (error != null) "$message\n${Log.getStackTraceString(error)}" else message
        Log.println(priority, AUTH_LOG_TAG, full)
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

    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(lock) {
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    fun saveSession(rawCookieHeader: String) {
        val map = parseCookieHeader(rawCookieHeader)
        val normalized = formatCookieHeader(map)
        val sapisid = map["SAPISID"]?.takeIf { it.isNotBlank() }
        val secure3p = map["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() }
        synchronized(lock) {
            prefs?.edit()?.apply {
                putString(KEY_RAW_COOKIE_STRING, normalized)
                if (sapisid != null) putString(KEY_SAPISID, sapisid) else remove(KEY_SAPISID)
                if (secure3p != null) putString(KEY_SECURE_3PAPISID, secure3p) else remove(KEY_SECURE_3PAPISID)
            }?.commit()
        }
        authLog(
            Log.INFO,
            "Saved session cookies (count=${map.size}, hasSAPISID=${sapisid != null}, has3PAPISID=${secure3p != null})",
        )
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

    /**
     * Restores persisted cookies from `clipflow_auth_prefs` back into Android's [CookieManager]
     * on app startup so WebView and browser-dependent components stay signed in across launches.
     */
    fun restoreCookiesToCookieManager(): Boolean {
        val raw = getRawCookieString() ?: return false
        val map = parseCookieHeader(raw)
        if (!hasAuthCookiePair(map)) return false
        return runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val entries = buildCookieRestoreEntries(map)
            for ((url, cookieLine) in entries) {
                cm.setCookie(url, cookieLine)
            }
            cm.flush()
            authLog(Log.INFO, "Restored ${map.size} session cookies into CookieManager")
            true
        }.getOrElse { err ->
            authLog(Log.WARN, "Failed restoring cookies into CookieManager", err)
            false
        }
    }

    fun clear() {
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

    internal fun parseCookieHeader(header: String?): LinkedHashMap<String, String> {
        val out = linkedMapOf<String, String>()
        if (header.isNullOrBlank()) return out
        header.split(';').forEach { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@forEach
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                out[name] = value
            }
        }
        return out
    }

    internal fun formatCookieHeader(map: Map<String, String>): String =
        map.entries.filter { it.key.isNotBlank() && it.value.isNotBlank() }
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

    internal fun buildCookieRestoreEntries(cookies: Map<String, String>): List<Pair<String, String>> {
        val entries = ArrayList<Pair<String, String>>(cookies.size * 2)
        for ((name, value) in cookies) {
            if (name.isBlank() || value.isBlank()) continue
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
