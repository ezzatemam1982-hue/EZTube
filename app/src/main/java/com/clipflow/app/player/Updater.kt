package com.clipflow.app.player

import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Direct In-App Self-Updater with Remote Version Control for EZTube.
 * Silently fetches release metadata and version control configuration in the background.
 * Supports mandatory (Force Update) and optional update dialogs.
 * Downloads the APK internally with percentage tracking and launches the package installer directly
 * via FileProvider without ever opening an external browser or displaying GitHub URLs to the user.
 */
object AppUpdate {
    private const val TAG = "EZTubeUpdate"

    internal const val RELEASES_URL = AppUpdateConfig.RELEASES_API_URL
    internal const val VERSION_CONTROL_URL = AppUpdateConfig.VERSION_CONTROL_URL

    private const val APK_ASSET = "ClipFlow.apk"
    private const val CHECK_EVERY_MS = 24 * 60 * 60 * 1000L
    private const val RETRY_AFTER_MS = 60 * 60 * 1000L
    private const val STARTUP_CHECK_DELAY_MS = 10_000L

    private const val KEY_NEXT_CHECK = "next_check"
    private const val KEY_RELEASE = "release"
    private const val KEY_CHECKED_BY = "checked_by"
    private const val KEY_INSTALLING = "installing"
    private const val KEY_PROMPTED = "prompted"
    private const val CHANNEL = "updates"
    internal const val ACTION_RESULT = "com.clipflow.UPDATE_RESULT"

    data class Release(
        val version: String,
        val apkUrl: String,
        val size: Long,
        val sha256: String?,
        val notes: List<ReleaseNotes> = emptyList(),
        val releaseBody: String = "",
    )

    data class ReleaseNotes(val version: String, val body: String) {
        val sections by lazy { parseNotes(body) }
    }

    data class NoteSection(val title: String?, val items: List<String>)

    data class VersionControl(
        val latestVersion: String,
        val minSupportedVersion: String,
        val forceUpdate: Boolean,
    )

    enum class Phase { Ready, Downloading, Installing, Failed }

    enum class UpdateType { None, Optional, Mandatory }

    var available by mutableStateOf<Release?>(null)
        private set
    var updateType by mutableStateOf(UpdateType.None)
        private set
    val isMandatoryUpdate: Boolean get() = updateType == UpdateType.Mandatory
    val isOptionalUpdate: Boolean get() = updateType == UpdateType.Optional

    var showMandatoryDialog by mutableStateOf(false)
    var showOptionalDialog by mutableStateOf(false)
    var manualCheckMessage by mutableStateOf<String?>(null)

    var phase by mutableStateOf(Phase.Ready)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var checking by mutableStateOf(false)
        private set

    internal var announced = false

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = MainScope()
    private var download: Deferred<File?>? = null
    private var downloadVersion: String? = null
    private var awaitingPermission = false
    private var pendingApk: File? = null

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val release = savedRelease()?.takeIf { isNewer(it.version) }
                val keep = release?.let { apkFile(it).name }
                dir().listFiles()?.forEach { if (it.name != keep) it.delete() }
                release
            }
            if (available == null && saved != null) {
                available = saved
            }
        }
    }

    /** Called on app startup; waits 10s on Dispatchers.IO then runs silent background update check. */
    fun checkIfDue() {
        scope.launch(Dispatchers.IO) {
            delay(STARTUP_CHECK_DELAY_MS)
            check(force = false)
        }
    }

    /** Manual check from Settings About: checks immediately and displays dialog or feedback. */
    fun checkNow() {
        scope.launch {
            check(force = true)
        }
    }

    fun dismissOptional() {
        showOptionalDialog = false
    }

    private suspend fun check(force: Boolean) {
        val newInstall = prefs.getString(KEY_CHECKED_BY, null) != BuildConfig.VERSION_NAME
        if (checking || (!force && !newInstall && System.currentTimeMillis() < prefs.getLong(KEY_NEXT_CHECK, 0L))) return
        checking = true

        val (versionControl, latestRelease) = withContext(Dispatchers.IO) {
            val vc = runCatching { fetchVersionControl() }.getOrNull()
            val rel = runCatching { fetchLatest() }.getOrNull()
            Pair(vc, rel)
        }
        checking = false

        val now = System.currentTimeMillis()
        if (versionControl == null && latestRelease == null) {
            Log.w(TAG, "Update check failed: both version control and releases API were unreachable")
            prefs.edit().putLong(KEY_NEXT_CHECK, now + RETRY_AFTER_MS)
                .putString(KEY_CHECKED_BY, BuildConfig.VERSION_NAME).apply()
            if (force) manualCheckMessage = "ERROR"
            return
        }

        val currentVersion = BuildConfig.VERSION_NAME
        val evaluatedType = when {
            versionControl != null -> {
                val vcType = evaluateUpdate(currentVersion, versionControl)
                if (vcType == UpdateType.None && latestRelease != null && isNewer(latestRelease.version)) {
                    UpdateType.Optional
                } else {
                    vcType
                }
            }
            latestRelease != null && isNewer(latestRelease.version) -> UpdateType.Optional
            else -> UpdateType.None
        }

        prefs.edit().putLong(KEY_NEXT_CHECK, now + CHECK_EVERY_MS)
            .putString(KEY_CHECKED_BY, BuildConfig.VERSION_NAME)
            .apply {
                if (latestRelease != null) putString(KEY_RELEASE, latestRelease.toJson())
                else remove(KEY_RELEASE)
            }
            .apply()

        Log.i(TAG, "Update check: evaluated $evaluatedType, latestRelease=${latestRelease?.version}, current=$currentVersion")

        if (phase == Phase.Downloading || phase == Phase.Installing) return

        updateType = evaluatedType
        if (evaluatedType != UpdateType.None) {
            val resolvedRelease = latestRelease ?: Release(
                version = versionControl?.latestVersion ?: currentVersion,
                apkUrl = "",
                size = 0L,
                sha256 = null,
            )
            available = resolvedRelease
            phase = Phase.Ready

            if (evaluatedType == UpdateType.Mandatory) {
                showMandatoryDialog = true
                showOptionalDialog = false
            } else {
                showOptionalDialog = true
                showMandatoryDialog = false
                if (!metered() && resolvedRelease.apkUrl.isNotBlank()) {
                    apkFor(resolvedRelease)
                }
            }
        } else {
            available = null
            showMandatoryDialog = false
            showOptionalDialog = false
            if (force) {
                manualCheckMessage = "UP_TO_DATE"
            }
        }
    }

    internal fun fetchVersionControl(): VersionControl? {
        val request = Request.Builder()
            .url(VERSION_CONTROL_URL)
            .header("User-Agent", "EZTube/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.string()
            return parseVersionControl(body)
        }
    }

    internal fun parseVersionControl(jsonString: String): VersionControl {
        val json = JSONObject(jsonString)
        val latest = json.optString("latest_version").trim().removePrefix("v").removePrefix("V")
        val min = json.optString("min_supported_version", latest).trim().removePrefix("v").removePrefix("V")
        val force = json.optBoolean("force_update", false)
        return VersionControl(latestVersion = latest, minSupportedVersion = min, forceUpdate = force)
    }

    internal fun evaluateUpdate(currentVersion: String, vc: VersionControl): UpdateType {
        val cmpLatest = compareVersions(vc.latestVersion, currentVersion)
        val cmpMin = compareVersions(vc.minSupportedVersion, currentVersion)
        val hasNewer = cmpLatest > 0
        val isBelowMin = cmpMin > 0
        return when {
            isBelowMin || (vc.forceUpdate && hasNewer) -> UpdateType.Mandatory
            hasNewer -> UpdateType.Optional
            else -> UpdateType.None
        }
    }

    /** 100% in-app APK download with progress bar and direct package installer launch. */
    fun install(context: Context) {
        val release = available ?: return
        if (phase == Phase.Downloading || phase == Phase.Installing) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            awaitingPermission = true
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }

        phase = Phase.Downloading
        progress = 0f
        scope.launch {
            val apk = withContext(Dispatchers.IO) {
                downloadApkWithDownloadManager(context, release)
            }
            if (apk == null || !apk.exists()) {
                phase = Phase.Failed
                return@launch
            }
            phase = Phase.Installing
            pendingApk = apk
            launchInstaller(context, apk)
        }
    }

    private suspend fun downloadApkWithDownloadManager(context: Context, release: Release): File? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: updatesDir()
        val targetFile = File(destDir, "EZTube-update.apk")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        if (downloadManager == null) {
            Log.w(TAG, "DownloadManager service unavailable, falling back to direct download")
            return runCatching { downloadApkDirect(release) }.getOrNull()
        }

        return try {
            val request = DownloadManager.Request(Uri.parse(release.apkUrl)).apply {
                setTitle("EZTube ${release.version}")
                setDescription(context.getString(R.string.app_update_downloading, ""))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "EZTube-update.apk")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)
            var completed = false
            var failed = false

            while (!completed && !failed) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                    val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx) else 0L
                    val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else release.size

                    if (total > 0 && downloaded > 0) {
                        val frac = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) {
                            progress = frac
                        }
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            completed = true
                            withContext(Dispatchers.Main) {
                                progress = 1f
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            failed = true
                            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                            Log.w(TAG, "DownloadManager failed with reason $reason")
                        }
                    }
                    cursor.close()
                } else {
                    cursor?.close()
                }

                if (!completed && !failed) {
                    delay(300)
                }
            }

            if (completed && targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else {
                Log.w(TAG, "DownloadManager did not produce valid file, falling back to direct download")
                runCatching { downloadApkDirect(release) }.getOrNull()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DownloadManager failed (${t.message}), falling back to direct download", t)
            runCatching { downloadApkDirect(release) }.getOrNull()
        }
    }

    private fun downloadApkDirect(release: Release): File {
        val updatesDir = updatesDir()
        val target = File(updatesDir, "EZTube-update.apk")
        val part = File(updatesDir, "EZTube-update.apk.part")

        progress = 0f
        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "EZTube/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "no body" }
            val total = release.size.takeIf { it > 0 } ?: body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) progress = (read.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
            if (target.exists()) target.delete()
            check(part.renameTo(target)) { "couldn't rename ${part.name}" }
        }
        progress = 1f
        return target
    }

    internal fun launchInstaller(context: Context, apk: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "update: launchInstaller failed", it)
            phase = Phase.Failed
        }
    }

    fun onWindowResumed(context: Context) {
        if (!awaitingPermission) return
        awaitingPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context.packageManager.canRequestPackageInstalls()) {
            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: updatesDir()
            val apk = pendingApk ?: File(destDir, "EZTube-update.apk").takeIf { it.exists() }
                ?: File(updatesDir(), "EZTube-update.apk").takeIf { it.exists() }
            if (apk != null) {
                launchInstaller(context, apk)
            } else {
                install(context)
            }
        }
    }

    internal fun onInstallResult(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) { phase = Phase.Failed; return }
                prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                prefs.edit().remove(KEY_INSTALLING).remove(KEY_PROMPTED).apply()
                phase = Phase.Ready
            }
            else -> {
                Log.w(TAG, "update: install failed ($status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)})")
                prefs.edit().remove(KEY_INSTALLING).remove(KEY_PROMPTED).apply()
                phase = Phase.Failed
            }
        }
    }

    internal fun onUpdated(context: Context) {
        val version = prefs.getString(KEY_INSTALLING, null) ?: return
        val prompted = prefs.getBoolean(KEY_PROMPTED, false)
        prefs.edit().remove(KEY_INSTALLING).remove(KEY_PROMPTED).apply()
        Log.i(TAG, "update: now on ${BuildConfig.VERSION_NAME} (installed $version)")
        if (prompted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.app_update_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(str(R.string.app_updated_title, BuildConfig.VERSION_NAME))
            .setContentText(str(R.string.app_updated_text))
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, notification) }
            .onFailure { Log.w(TAG, "update: couldn't post updated notification (${it.message})") }
    }

    private fun fetchLatest(): Release? {
        val request = Request.Builder().url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EZTube/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body.string().trim()
            return if (body.startsWith("[")) {
                parseReleases(JSONArray(body), BuildConfig.VERSION_NAME)
            } else {
                parseRelease(JSONObject(body))
            }
        }
    }

    internal fun parseReleases(json: JSONArray, current: String): Release? {
        val published = (0 until json.length()).map { json.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
        val latest = published.mapNotNull { parseRelease(it) }.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
            ?: return null
        val notes = published
            .map { ReleaseNotes(it.optString("tag_name").trim().removePrefix("v").removePrefix("V"), it.optString("body")) }
            .filter { compareVersions(it.version, current) > 0 && compareVersions(it.version, latest.version) <= 0 }
            .filter { it.sections.isNotEmpty() }
            .sortedWith { a, b -> compareVersions(b.version, a.version) }
        val body = latest.releaseBody.ifBlank { notes.firstOrNull()?.body.orEmpty() }
        return latest.copy(notes = notes, releaseBody = body)
    }

    internal fun selectApkAssetName(assetNames: List<String>, abi: String? = null): String? {
        val apks = assetNames.filter {
            it.endsWith(".apk", ignoreCase = true) &&
                !it.contains("foss", ignoreCase = true) &&
                !it.contains("izzy", ignoreCase = true)
        }
        val isArmV7 = abi?.contains("v7", ignoreCase = true) == true
        if (isArmV7) {
            val legacy = apks.firstOrNull { it.contains("legacy", ignoreCase = true) || it.contains("armeabi-v7a", ignoreCase = true) }
            if (legacy != null) return legacy
        }
        return apks.firstOrNull {
            it.equals("EZTube.apk", ignoreCase = true) || it.equals(APK_ASSET, ignoreCase = true)
        } ?: abi?.takeIf { it.isNotBlank() }?.let { targetAbi ->
            apks.firstOrNull { it.contains(targetAbi, ignoreCase = true) }
        } ?: apks.firstOrNull {
            !it.contains("legacy", ignoreCase = true) &&
                it.matches(Regex("""EZTube-v?[0-9.]+\.apk""", RegexOption.IGNORE_CASE))
        } ?: apks.firstOrNull { it.endsWith("arm64-v8a-release-github.apk", ignoreCase = true) }
          ?: apks.firstOrNull { it.contains("arm64", ignoreCase = true) }
          ?: apks.firstOrNull { it.contains("universal", ignoreCase = true) }
          ?: apks.firstOrNull { !it.contains("legacy", ignoreCase = true) }
          ?: apks.firstOrNull()
    }

    internal fun selectApkAsset(assets: List<JSONObject>, abi: String? = Build.SUPPORTED_ABIS.firstOrNull()): JSONObject? {
        val chosen = selectApkAssetName(assets.map { it.optString("name") }, abi) ?: return null
        return assets.firstOrNull { it.optString("name") == chosen }
    }

    internal fun parseRelease(json: JSONObject, abi: String? = Build.SUPPORTED_ABIS.firstOrNull()): Release? {
        val version = json.optString("tag_name").trim().removePrefix("v").removePrefix("V").ifBlank { return null }
        val assets = json.optJSONArray("assets") ?: return null
        val assetList = (0 until assets.length()).map { assets.getJSONObject(it) }
        val apk = selectApkAsset(assetList, abi) ?: return null
        val body = json.optString("body")
        return Release(
            version = version,
            apkUrl = apk.optString("browser_download_url").ifBlank { return null },
            size = apk.optLong("size"),
            sha256 = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
            notes = listOf(ReleaseNotes(version, body)).filter { it.sections.isNotEmpty() },
            releaseBody = body,
        )
    }

    internal fun parseNotes(body: String): List<NoteSection> {
        val sections = mutableListOf<NoteSection>()
        var title: String? = null
        val items = mutableListOf<String>()
        fun flush() {
            if (items.isNotEmpty()) sections += NoteSection(title, items.toList())
            items.clear()
        }
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (line == "---") break
            when {
                line.startsWith("#") -> { flush(); title = plain(line.trimStart('#').trim()).ifBlank { null } }
                line.startsWith("- ") || line.startsWith("* ") -> items += plain(line.substring(2).trim())
                line.isNotEmpty() && items.isNotEmpty() && raw.startsWith("  ") -> items[items.lastIndex] += " " + plain(line)
            }
        }
        flush()
        return sections
    }

    private fun plain(markdown: String) = markdown
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")

    internal fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun isNewer(version: String) = compareVersions(version, BuildConfig.VERSION_NAME) > 0

    private fun metered() = app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered != false

    private fun apkFor(release: Release): Deferred<File?> {
        download?.takeIf { it.isActive && downloadVersion == release.version }?.let { return it }
        downloadVersion = release.version
        return scope.async(Dispatchers.IO) {
            runCatching { downloadApkDirect(release) }
                .onFailure { Log.w(TAG, "update: download of ${release.version} failed (${it.message})") }
                .getOrNull()
        }.also { download = it }
    }

    fun updatesDir(): File = File(app.cacheDir, "updates").apply { mkdirs() }
    private fun dir() = File(app.cacheDir, "update").apply { mkdirs() }
    private fun apkFile(release: Release) = File(dir(), "ClipFlow-${release.version}.apk")

    private fun savedRelease(): Release? = prefs.getString(KEY_RELEASE, null)?.let {
        runCatching {
            val o = JSONObject(it)
            val notes = o.optJSONArray("notes") ?: JSONArray()
            Release(
                o.getString("version"),
                o.getString("url"),
                o.getLong("size"),
                o.optString("sha256").ifBlank { null },
                (0 until notes.length()).map { notes.getJSONObject(it).let { n -> ReleaseNotes(n.getString("version"), n.getString("body")) } },
                o.optString("body", ""),
            )
        }.getOrNull()
    }

    private fun Release.toJson() =
        JSONObject().put("version", version).put("url", apkUrl).put("size", size).put("sha256", sha256 ?: "")
            .put("body", releaseBody)
            .put("notes", JSONArray(notes.map { JSONObject().put("version", it.version).put("body", it.body) }))
            .toString()
}

/** PackageInstaller results, and the first start of a freshly installed version. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppUpdate.ACTION_RESULT -> AppUpdate.onInstallResult(context, intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> AppUpdate.onUpdated(context)
        }
    }
}

/**
 * Top-level Dialog host for EZTube's direct in-app updates.
 * Renders mandatory non-dismissible dialogs, optional dismissible dialogs, and toasts.
 */
@Composable
fun AppUpdateDialogs() {
    val context = LocalContext.current
    val phase = AppUpdate.phase
    val progress = AppUpdate.progress

    val manualMsg = AppUpdate.manualCheckMessage
    LaunchedEffect(manualMsg) {
        if (manualMsg == "UP_TO_DATE") {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.app_update_latest_installed),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            AppUpdate.manualCheckMessage = null
        } else if (manualMsg == "ERROR") {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.player_error_offline),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            AppUpdate.manualCheckMessage = null
        }
    }

    if (AppUpdate.showMandatoryDialog) {
        BackHandler(enabled = true) {}
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            icon = {
                Icon(
                    Icons.Rounded.SystemUpdateAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    stringResource(R.string.app_update_required_title),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.app_update_required_message),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val mandatoryNotes = AppUpdate.available?.releaseBody?.trim().orEmpty()
                    if (mandatoryNotes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.app_update_whats_new),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = mandatoryNotes,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (phase == AppUpdate.Phase.Downloading) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        )
                        Text(
                            stringResource(R.string.app_update_downloading, (progress * 100).toInt().toString()),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (phase == AppUpdate.Phase.Installing) {
                        Text(
                            stringResource(R.string.app_update_installing),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (phase == AppUpdate.Phase.Failed) {
                        Text(
                            stringResource(R.string.player_error_offline),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { AppUpdate.install(context) },
                    enabled = phase != AppUpdate.Phase.Downloading && phase != AppUpdate.Phase.Installing,
                ) {
                    Text(
                        if (phase == AppUpdate.Phase.Failed) {
                            stringResource(R.string.app_update_try_again)
                        } else {
                            stringResource(R.string.app_update_now)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    } else if (AppUpdate.showOptionalDialog) {
        val ver = AppUpdate.available?.version.orEmpty()
        AlertDialog(
            onDismissRequest = { AppUpdate.dismissOptional() },
            properties = DialogProperties(
                dismissOnBackPress = phase != AppUpdate.Phase.Downloading && phase != AppUpdate.Phase.Installing,
                dismissOnClickOutside = phase != AppUpdate.Phase.Downloading && phase != AppUpdate.Phase.Installing,
            ),
            icon = {
                Icon(
                    Icons.Rounded.SystemUpdateAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    stringResource(R.string.app_update_optional_title),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.app_update_optional_message, ver),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val optionalNotes = AppUpdate.available?.releaseBody?.trim().orEmpty()
                    if (optionalNotes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.app_update_whats_new),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = optionalNotes,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (phase == AppUpdate.Phase.Downloading) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        )
                        Text(
                            stringResource(R.string.app_update_downloading, (progress * 100).toInt().toString()),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (phase == AppUpdate.Phase.Installing) {
                        Text(
                            stringResource(R.string.app_update_installing),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (phase == AppUpdate.Phase.Failed) {
                        Text(
                            stringResource(R.string.player_error_offline),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { AppUpdate.install(context) },
                    enabled = phase != AppUpdate.Phase.Downloading && phase != AppUpdate.Phase.Installing,
                ) {
                    Text(
                        if (phase == AppUpdate.Phase.Failed) {
                            stringResource(R.string.app_update_try_again)
                        } else {
                            stringResource(R.string.app_update_now)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { AppUpdate.dismissOptional() },
                    enabled = phase != AppUpdate.Phase.Downloading && phase != AppUpdate.Phase.Installing,
                ) {
                    Text(stringResource(R.string.app_update_later))
                }
            },
        )
    }
}

/**
 * The header's Update pill, beside the profile picture; absent when there's nothing newer.
 */
@Composable
fun UpdateAction() {
    val release = AppUpdate.available ?: return
    val c = MaterialTheme.colorScheme
    val phase = AppUpdate.phase
    var sheet by remember { mutableStateOf(false) }
    val appear = remember { Animatable(if (AppUpdate.announced) 1f else 0f) }
    LaunchedEffect(Unit) {
        AppUpdate.announced = true
        appear.animateTo(1f, tween(420, easing = Motion.Pop))
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * v
                scaleY = 0.6f + 0.4f * v
            }
            .pressable { sheet = true }
            .height(44.dp)
            .widthIn(min = 104.dp)
            .clip(CircleShape)
            .background(c.primary)
            .drawBehind {
                if (phase == AppUpdate.Phase.Downloading) {
                    drawRect(c.onPrimary.copy(alpha = 0.22f), size = Size(size.width * AppUpdate.progress, size.height))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (phase == AppUpdate.Phase.Failed) Icons.Rounded.Refresh else Icons.Rounded.SystemUpdateAlt,
                contentDescription = null,
                tint = c.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                when (phase) {
                    AppUpdate.Phase.Ready -> stringResource(R.string.app_update_pill)
                    AppUpdate.Phase.Downloading -> stringResource(R.string.app_update_percent, (AppUpdate.progress * 100).toInt().toString())
                    AppUpdate.Phase.Installing -> stringResource(R.string.app_update_pill_installing)
                    AppUpdate.Phase.Failed -> stringResource(R.string.app_update_pill_retry)
                },
                color = c.onPrimary,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
    if (sheet) WhatsNewSheet(release, onDismiss = { sheet = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatsNewSheet(release: AppUpdate.Release, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val phase = AppUpdate.phase
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.app_update_whats_new),
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            listOfNotNull(
                "EZTube ${release.version}",
                release.size.takeIf { it > 0 }?.let { stringResource(R.string.app_update_size_mb, "%.1f".format(Locale.US, it / 1_048_576.0)) },
            ).joinDots(),
            color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 14.dp),
        )
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (release.notes.isEmpty()) {
                Text(stringResource(R.string.app_update_no_notes), color = c.onSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp)
            }
            val several = release.notes.size > 1
            release.notes.forEach { notes ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (several) {
                        Text(notes.version, color = c.primary, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    notes.sections.forEach { NoteSectionBlock(it) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .pressable(enabled = phase == AppUpdate.Phase.Ready || phase == AppUpdate.Phase.Failed) { AppUpdate.install(context) }
                .height(54.dp)
                .clip(CircleShape)
                .background(c.primary)
                .drawBehind {
                    if (phase == AppUpdate.Phase.Downloading) {
                        drawRect(c.onPrimary.copy(alpha = 0.22f), size = Size(size.width * AppUpdate.progress, size.height))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (phase) {
                    AppUpdate.Phase.Ready -> stringResource(R.string.app_update_button)
                    AppUpdate.Phase.Downloading -> stringResource(R.string.app_update_downloading, (AppUpdate.progress * 100).toInt().toString())
                    AppUpdate.Phase.Installing -> stringResource(R.string.app_update_installing)
                    AppUpdate.Phase.Failed -> stringResource(R.string.app_update_try_again)
                },
                color = c.onPrimary,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoteSectionBlock(section: AppUpdate.NoteSection) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        section.title?.let { Text(it, color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        section.items.forEach { item ->
            Row {
                Box(Modifier.padding(top = 8.dp, end = 12.dp).size(5.dp).clip(CircleShape).background(c.primary))
                Text(item, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
