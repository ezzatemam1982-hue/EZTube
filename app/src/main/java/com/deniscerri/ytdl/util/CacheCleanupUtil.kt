package com.deniscerri.ytdl.util

import android.content.Context
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.clipflow.app.player.StreamCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheCleanupUtil {
    private val PRESERVED_CACHE_ENTRIES = setOf("clipflow_yt_auth_cookies.txt", "update", "updates")
    private const val PREF_AUTO_CLEAR_CACHE = "auto_clear_cache"
    private const val PREF_LAST_CACHE_CLEAR = "last_cache_clear_time"

    fun measureClearableCacheBytes(context: Context): Long {
        fun dirBytes(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            if (dir.isFile) return dir.length()
            return dir.listFiles().orEmpty().sumOf { child ->
                if (child.name in PRESERVED_CACHE_ENTRIES) 0L else dirBytes(child)
            }
        }
        return dirBytes(context.cacheDir) + dirBytes(context.codeCacheDir) + dirBytes(context.externalCacheDir)
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearAppTransientCaches(context: Context): Long {
        StreamCache.invalidateAll()
        runCatching {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
        }
        fun wipeChildren(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles().orEmpty().forEach { child ->
                if (child.name !in PRESERVED_CACHE_ENTRIES) {
                    runCatching { child.deleteRecursively() }
                }
            }
        }
        wipeChildren(context.cacheDir)
        wipeChildren(context.codeCacheDir)
        wipeChildren(context.externalCacheDir)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putLong(PREF_LAST_CACHE_CLEAR, System.currentTimeMillis()).apply()

        return measureClearableCacheBytes(context)
    }

    suspend fun checkAndAutoClean(context: Context) = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getString(PREF_AUTO_CLEAR_CACHE, "size_250mb") ?: "size_250mb"
        if (mode == "off") return@withContext

        val now = System.currentTimeMillis()
        val lastClear = prefs.getLong(PREF_LAST_CACHE_CLEAR, 0L)

        if (mode == "days_7") {
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
            if (now - lastClear >= sevenDaysMs) {
                clearAppTransientCaches(context)
            }
        } else if (mode == "size_250mb") {
            val bytes = measureClearableCacheBytes(context)
            val threshold = 250L * 1024L * 1024L
            if (bytes > threshold) {
                clearAppTransientCaches(context)
            }
        }
    }
}
