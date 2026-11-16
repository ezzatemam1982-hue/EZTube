package com.clipflow.app.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deniscerri.ytdl.R
import java.io.File

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

    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(Mode.Default)
        private set
    var preset by mutableStateOf<Preset?>(null)
        private set
    var customPath by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        val savedMode = p.getString(KEY_MODE, null)
        val savedPreset = Preset.of(p.getString(KEY_PRESET, null))
        val savedPath = p.getString(KEY_CUSTOM_PATH, null)?.takeIf { File(it).exists() }
        when {
            savedMode == Mode.CustomFile.name && savedPath != null -> {
                mode = Mode.CustomFile
                preset = null
                customPath = savedPath
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

    fun selectPreset(choice: Preset) {
        deleteCustomFile(customPath)
        mode = Mode.Preset
        preset = choice
        customPath = null
        prefs?.edit()
            ?.putString(KEY_MODE, Mode.Preset.name)
            ?.putString(KEY_PRESET, choice.id)
            ?.remove(KEY_CUSTOM_PATH)
            ?.apply()
    }

    fun saveGalleryUri(context: Context, uri: Uri): Boolean = runCatching {
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val target = File(dir, "avatar_${System.currentTimeMillis()}.img")
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use { src ->
            target.outputStream().use { dst -> src.copyTo(dst) }
        }
        if (!target.exists() || target.length() <= 0L) {
            target.delete()
            return false
        }
        val old = customPath
        mode = Mode.CustomFile
        preset = null
        customPath = target.absolutePath
        prefs?.edit()
            ?.putString(KEY_MODE, Mode.CustomFile.name)
            ?.remove(KEY_PRESET)
            ?.putString(KEY_CUSTOM_PATH, target.absolutePath)
            ?.apply()
        if (old != target.absolutePath) deleteCustomFile(old)
        true
    }.getOrDefault(false)

    fun resetToDefault() {
        deleteCustomFile(customPath)
        mode = Mode.Default
        preset = null
        customPath = null
        prefs?.edit()
            ?.remove(KEY_MODE)
            ?.remove(KEY_PRESET)
            ?.remove(KEY_CUSTOM_PATH)
            ?.apply()
    }

    fun normalizeGoogleAvatarUrl(url: String?): String? {
        val trimmed = url?.trim()?.ifBlank { null } ?: return null
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    private fun deleteCustomFile(path: String?) {
        if (!path.isNullOrBlank()) {
            runCatching { File(path).delete() }
        }
    }
}
