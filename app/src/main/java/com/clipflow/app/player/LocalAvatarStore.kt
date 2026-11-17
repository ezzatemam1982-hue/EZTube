package com.clipflow.app.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deniscerri.ytdl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * UI-only local customization for the profile avatar badge shown in the top bar and Settings.
 * Completely independent of Google Sign-In/OAuth/cookie session state.
 */
object LocalAvatarStore {
    enum class Preset(
        val id: String,
        @DrawableRes val drawableRes: Int,
        val bgColor: Long,
    ) {
        Youtube("bolt", R.drawable.avatar_cartoon_1, 0xFFFFE2DE),
        Music("music", R.drawable.avatar_cartoon_2, 0xFFE0E7FF),
        Rose("play", R.drawable.avatar_cartoon_3, 0xFFFCE7F3),
        Star("star", R.drawable.avatar_cartoon_4, 0xFFFEF3C7),
        Audio("headphones", R.drawable.avatar_cartoon_5, 0xFFD1FAE5),
        Rocket("rocket", R.drawable.avatar_cartoon_6, 0xFFDBEAFE),
        Pets("pets", R.drawable.avatar_cartoon_7, 0xFFFFEDD5),
        Minimal("face", R.drawable.avatar_cartoon_8, 0xFFE0F2FE);

        companion object {
            fun of(id: String?): Preset? = entries.firstOrNull { it.id == id }
        }
    }

    enum class Mode { Default, Preset, CustomFile }

    private const val PREFS_NAME = "local_avatar"
    private const val KEY_MODE = "mode"
    private const val KEY_PRESET = "preset_id"
    private const val KEY_CUSTOM_PATH = "custom_path"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val verifiedFiles = ConcurrentHashMap<String, File>()
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(Mode.Default)
        private set
    var preset by mutableStateOf<Preset?>(null)
        private set
    var customPath by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        val savedMode = p.getString(KEY_MODE, null)
        val savedPreset = Preset.of(p.getString(KEY_PRESET, null))
        val savedPath = p.getString(KEY_CUSTOM_PATH, null)?.takeIf { it.isNotBlank() }
        when {
            savedMode == Mode.CustomFile.name && savedPath != null -> {
                mode = Mode.CustomFile
                preset = null
                customPath = savedPath
                // Validate file existence asynchronously on Dispatchers.IO instead of stalling app startup.
                ioScope.launch {
                    val file = File(savedPath)
                    if (file.exists() && file.length() > 0L) {
                        verifiedFiles[savedPath] = file
                    } else {
                        withContext(Dispatchers.Main.immediate) {
                            if (mode == Mode.CustomFile && customPath == savedPath) {
                                mode = Mode.Default
                                preset = null
                                customPath = null
                            }
                        }
                        p.edit().remove(KEY_MODE).remove(KEY_CUSTOM_PATH).apply()
                    }
                }
            }
            savedMode == Mode.Preset.name && savedPreset != null -> {
                mode = Mode.Preset
                preset = savedPreset
                customPath = null
            }
            else -> {
                mode = Mode.Default
                preset = null
                customPath = null
            }
        }
    }

    /** Returns an already-verified local avatar [File] without touching the filesystem. */
    fun cachedFileFor(path: String?): File? = path?.let { verifiedFiles[it] }

    /** Validates and caches a custom avatar [File] strictly on [Dispatchers.IO]. */
    suspend fun resolveCustomFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        verifiedFiles[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            val candidate = File(path).takeIf { it.exists() && it.length() > 0L }
            if (candidate != null) {
                verifiedFiles[path] = candidate
            } else {
                verifiedFiles.remove(path)
                withContext(Dispatchers.Main.immediate) {
                    if (mode == Mode.CustomFile && customPath == path) {
                        mode = Mode.Default
                        preset = null
                        customPath = null
                        prefs?.edit()?.remove(KEY_MODE)?.remove(KEY_CUSTOM_PATH)?.apply()
                    }
                }
            }
            candidate
        }
    }

    fun selectPreset(choice: Preset) {
        val old = customPath
        if (old != null) verifiedFiles.remove(old)
        mode = Mode.Preset
        preset = choice
        customPath = null
        prefs?.edit()
            ?.putString(KEY_MODE, Mode.Preset.name)
            ?.putString(KEY_PRESET, choice.id)
            ?.remove(KEY_CUSTOM_PATH)
            ?.apply()
        deleteCustomFileAsync(old)
    }

    /** Copies [uri] to internal storage strictly on [Dispatchers.IO] without stalling the Main Thread. */
    suspend fun saveGalleryUriAsync(context: Context, uri: Uri): Boolean {
        val appContext = context.applicationContext
        val savedFile = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(appContext.filesDir, "avatars").apply { mkdirs() }
                val target = File(dir, "avatar_${System.currentTimeMillis()}.img")
                val input = appContext.contentResolver.openInputStream(uri) ?: return@runCatching null
                input.use { src ->
                    target.outputStream().use { dst -> src.copyTo(dst) }
                }
                if (!target.exists() || target.length() <= 0L) {
                    target.delete()
                    return@runCatching null
                }
                target
            }.getOrNull()
        } ?: return false

        val newPath = savedFile.absolutePath
        verifiedFiles[newPath] = savedFile
        val old = withContext(Dispatchers.Main.immediate) {
            val previous = customPath
            mode = Mode.CustomFile
            preset = null
            customPath = newPath
            prefs?.edit()
                ?.putString(KEY_MODE, Mode.CustomFile.name)
                ?.remove(KEY_PRESET)
                ?.putString(KEY_CUSTOM_PATH, newPath)
                ?.apply()
            previous
        }
        if (old != null && old != newPath) {
            verifiedFiles.remove(old)
            deleteCustomFileAsync(old)
        }
        return true
    }

    fun resetToDefault() {
        val old = customPath
        if (old != null) verifiedFiles.remove(old)
        mode = Mode.Default
        preset = null
        customPath = null
        prefs?.edit()
            ?.remove(KEY_MODE)
            ?.remove(KEY_PRESET)
            ?.remove(KEY_CUSTOM_PATH)
            ?.apply()
        deleteCustomFileAsync(old)
    }

    fun normalizeGoogleAvatarUrl(url: String?): String? {
        val trimmed = url?.trim()?.ifBlank { null } ?: return null
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    private fun deleteCustomFileAsync(path: String?) {
        if (!path.isNullOrBlank()) {
            ioScope.launch {
                runCatching { File(path).delete() }
            }
        }
    }
}
