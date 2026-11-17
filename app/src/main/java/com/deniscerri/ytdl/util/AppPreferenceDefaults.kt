package com.deniscerri.ytdl.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.work.background.CleanUpLeftoverDownloads
import java.io.File

/**
 * Centralizes EZTube v1.0.3 defaults, legacy ClipFlow -> EZTube folder path migrations,
 * locked/hidden preference enforcement, and per-screen Reset restoration.
 */
object AppPreferenceDefaults {

    private const val KEY_V103_MIGRATED = "ez_v103_pref_defaults"
    private const val DEFAULT_SEARCH_ENGINE = "ytsearch"
    private const val DEFAULT_CONCURRENT_FRAGMENTS = 4
    private const val DEFAULT_CONCURRENT_DOWNLOADS = 2

    fun migrateAndApply(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val allSwipeGestures = context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        val needsInitialV103 = !prefs.getBoolean(KEY_V103_MIGRATED, false)
        val needsLocks = !prefs.getBoolean("check_clipboard_home", false) ||
            prefs.getString("label_visibility", null) != "always" ||
            prefs.getStringSet("swipe_gesture", null) != allSwipeGestures ||
            !prefs.getBoolean("swipe_gestures_download_card", false) ||
            prefs.getBoolean("cache_downloads", true) ||
            prefs.getBoolean("no_part", true) ||
            prefs.getBoolean("keep_cache", true) ||
            !prefs.getBoolean("trim_filenames", false) ||
            prefs.getBoolean("restrict_filenames", true) ||
            !prefs.getBoolean("use_cookies", false) ||
            prefs.getBoolean("quick_download", true) ||
            prefs.getBoolean("log_downloads", true) ||
            prefs.getBoolean("use_alarm_for_scheduling", true)
        val musicPath = prefs.getString("music_path", "").orEmpty()
        val videoPath = prefs.getString("video_path", "").orEmpty()
        val commandPath = prefs.getString("command_path", "").orEmpty()
        val backupPath = prefs.getString("backup_path", "").orEmpty()
        val cachePath = prefs.getString("cache_path", "").orEmpty()
        val needsMigration = musicPath.isBlank() || isLegacyClipFlowPath(musicPath, "/Audio") ||
            videoPath.isBlank() || isLegacyClipFlowPath(videoPath, "/Video") ||
            commandPath.isBlank() || isLegacyClipFlowPath(commandPath, "/Command") ||
            (backupPath.isNotBlank() && isLegacyClipFlowPath(backupPath, "/Backups")) ||
            cachePath.contains("clipflow_cache", ignoreCase = true)

        if (!needsInitialV103 && !needsLocks && !needsMigration) return

        prefs.edit {
            enforceLockedPreferences(context, this)
            migrateLegacyPaths(prefs, this)
            if (needsInitialV103) {
                putString("search_engine", DEFAULT_SEARCH_ENGINE)
                putBoolean("search_suggestions", true)
                putInt("concurrent_fragments", DEFAULT_CONCURRENT_FRAGMENTS)
                putInt("concurrent_downloads", DEFAULT_CONCURRENT_DOWNLOADS)
                putString("cleanup_leftover_downloads", CleanUpLeftoverDownloads.DEFAULT_INTERVAL)
                putBoolean(KEY_V103_MIGRATED, true)
            }
        }
    }

    fun applyGeneralResetDefaults(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(commit = true) {
            putString("search_engine", DEFAULT_SEARCH_ENGINE)
            putBoolean("search_suggestions", true)
            enforceGeneralLocks(context, this)
        }
    }

    fun applyFolderResetDefaults(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(commit = true) {
            putString("music_path", FileUtil.getDefaultAudioPath())
            putString("video_path", FileUtil.getDefaultVideoPath())
            putString("command_path", FileUtil.getDefaultCommandPath())
            putString("cache_path", "")
            enforceFolderLocks(this)
        }
    }

    fun applyDownloadResetDefaults(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit(commit = true) {
            putInt("concurrent_fragments", DEFAULT_CONCURRENT_FRAGMENTS)
            putInt("concurrent_downloads", DEFAULT_CONCURRENT_DOWNLOADS)
            putString("cleanup_leftover_downloads", CleanUpLeftoverDownloads.DEFAULT_INTERVAL)
            enforceDownloadLocks(this)
        }
        CleanUpLeftoverDownloads.schedule(context, CleanUpLeftoverDownloads.DEFAULT_INTERVAL)
    }

    private fun enforceLockedPreferences(context: Context, editor: SharedPreferences.Editor) {
        enforceGeneralLocks(context, editor)
        enforceFolderLocks(editor)
        enforceDownloadLocks(editor)
    }

    private fun enforceGeneralLocks(context: Context, editor: SharedPreferences.Editor) {
        editor.putBoolean("check_clipboard_home", true)
        editor.putString("label_visibility", "always")
        val allSwipeGestures = context.resources.getStringArray(R.array.swipe_gestures_values).toSet()
        editor.putStringSet("swipe_gesture", allSwipeGestures)
        editor.putBoolean("swipe_gestures_download_card", true)
    }

    private fun enforceFolderLocks(editor: SharedPreferences.Editor) {
        editor.putBoolean("cache_downloads", false)
        editor.putBoolean("no_part", false)
        editor.putBoolean("keep_cache", false)
        editor.putBoolean("trim_filenames", true)
        editor.putBoolean("restrict_filenames", false)
    }

    private fun enforceDownloadLocks(editor: SharedPreferences.Editor) {
        editor.putBoolean("use_cookies", true)
        editor.putBoolean("quick_download", false)
        editor.putBoolean("log_downloads", false)
        editor.putBoolean("use_alarm_for_scheduling", false)
    }

    private fun migrateLegacyPaths(prefs: SharedPreferences, editor: SharedPreferences.Editor) {
        migrateFolderKey(prefs, editor, "music_path", "/Audio", FileUtil.getDefaultAudioPath())
        migrateFolderKey(prefs, editor, "video_path", "/Video", FileUtil.getDefaultVideoPath())
        migrateFolderKey(prefs, editor, "command_path", "/Command", FileUtil.getDefaultCommandPath())
        val backupPath = prefs.getString("backup_path", "").orEmpty()
        if (backupPath.isNotBlank() && isLegacyClipFlowPath(backupPath, "/Backups")) {
            editor.putString("backup_path", FileUtil.getDefaultApplicationPath() + "/Backups")
        }
        val cachePath = prefs.getString("cache_path", "").orEmpty()
        if (cachePath.contains("clipflow_cache", ignoreCase = true)) {
            editor.putString("cache_path", "")
        }
    }

    private fun migrateFolderKey(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor,
        key: String,
        suffix: String,
        defaultPath: String,
    ) {
        val current = prefs.getString(key, "").orEmpty()
        if (current.isBlank() || isLegacyClipFlowPath(current, suffix)) {
            editor.putString(key, defaultPath)
        }
    }

    internal fun isLegacyClipFlowPath(path: String, suffix: String): Boolean {
        val normalized = path.trim().trimEnd('/', File.separatorChar)
        val legacyDefault = runCatching {
            (FileUtil.getLegacyApplicationPath() + suffix).trimEnd('/', File.separatorChar)
        }.getOrNull()
        return (legacyDefault != null && normalized.equals(legacyDefault, ignoreCase = true)) ||
            normalized.endsWith("/Download/ClipFlow$suffix", ignoreCase = true) ||
            normalized.endsWith("/Downloads/ClipFlow$suffix", ignoreCase = true)
    }
}
