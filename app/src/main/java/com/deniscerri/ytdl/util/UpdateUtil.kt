package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.core.packages.PackageBase.Companion.sharedClient
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.models.GithubReleaseAsset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException


class UpdateUtil(var context: Context) {
    private val tag = "UpdateUtil"
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val channelMap = mapOf(
        Pair<String, RuntimeManager.UpdateChannel>("stable", RuntimeManager.UpdateChannel.STABLE),
        Pair<String, RuntimeManager.UpdateChannel>("nightly", RuntimeManager.UpdateChannel.NIGHTLY),
        Pair<String, RuntimeManager.UpdateChannel>("master", RuntimeManager.UpdateChannel.MASTER)
    )

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun tryGetNewVersion() : Result<GithubRelease> {
        try {
            val skippedVersions = sharedPreferences.getString("skip_updates", "")?.split(",")?.distinct()?.toMutableList() ?: mutableListOf()
            if (sharedPreferences.getBoolean("update_nightly", false)) return tryGetNewNightly(skippedVersions)
            val res = getGithubReleases()

            if (res.isEmpty()){
                return Result.failure(Error(context.getString(R.string.network_error)))
            }
            sharedPreferences.edit().putLong(APP_LAST_CHECK_KEY, System.currentTimeMillis()).apply()

            val currentVersion = BuildConfig.VERSION_NAME
            val useBeta = sharedPreferences.getBoolean("update_beta", false)
            var isInLatest: Boolean

            var v: GithubRelease
            if (useBeta) {
                v = res.firstOrNull { it.tag_name.contains("beta", true) } ?: res.firstOrNull()
                    ?: return Result.failure(Error(context.getString(R.string.you_are_in_latest_version)))
                val stableV = res.firstOrNull { !it.tag_name.contains("beta", true) }

                // If in beta but latest stable is higher, promote to stable.
                if (stableV != null && compareVersionTags(currentVersion, stableV.tag_name) < 0) {
                    isInLatest = false
                    v = stableV
                } else {
                    isInLatest = compareVersionTags(currentVersion, v.tag_name) >= 0
                }
            } else {
                v = res.firstOrNull { !it.tag_name.contains("beta", true) }
                    ?: return Result.failure(Error(context.getString(R.string.you_are_in_latest_version)))

                // If current version is beta and user switches to stable, allow updating to latest stable.
                isInLatest = if (currentVersion.contains("beta", true)) {
                    false
                } else {
                    compareVersionTags(currentVersion, v.tag_name) >= 0
                }
            }

            if (skippedVersions.contains(v.tag_name)) isInLatest = true
            if (isInLatest) return Result.failure(Error(context.getString(R.string.you_are_in_latest_version)))
            return Result.success(v)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    private fun tryGetNewNightly(skippedVersions: List<String>) : Result<GithubRelease> {
        val res = fetchReleases()
        if (res.isEmpty()) return Result.failure(Error(context.getString(R.string.network_error)))
        sharedPreferences.edit().putLong(APP_LAST_CHECK_KEY, System.currentTimeMillis()).apply()

        // nightlies share the stable version name, so a nightly is new if it was published after this app was installed/updated
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val nightly = res.firstOrNull { it.tag_name.startsWith("nightly-") }
        if (nightly == null || nightly.published_at.time <= installedAt || skippedVersions.contains(nightly.tag_name)) {
            return Result.failure(Error(context.getString(R.string.you_are_in_latest_version)))
        }
        return Result.success(nightly)
    }

    // the nightly pre-release isn't versioned, keep it out of the stable/beta checks and the changelog
    fun getGithubReleases(): List<GithubRelease> = fetchReleases().filter { !it.tag_name.startsWith("nightly-") }

    private fun fetchReleases(): List<GithubRelease> {
        val conn: HttpURLConnection
        var json = listOf<GithubRelease>()
        try {
            conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "EZTube/${BuildConfig.VERSION_NAME}")
            conn.connectTimeout = 10000
            conn.readTimeout = 5000
            if (conn.responseCode < 300) {
                val myType = object : TypeToken<List<GithubRelease>>() {}.type
                json = Gson().fromJson(InputStreamReader(conn.inputStream), myType) ?: emptyList()
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        return json
    }

    data class YTDLPUpdateResponse (
        val status: YTDLPUpdateStatus,
        val message: String = ""
    )

    enum class YTDLPUpdateStatus {
        DONE, ALREADY_UP_TO_DATE, PROCESSING, ERROR
    }

    suspend fun updateYTDL(c: String? = null) : YTDLPUpdateResponse =
        withContext(Dispatchers.IO){
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (updatingYTDL) {
                return@withContext YTDLPUpdateResponse(YTDLPUpdateStatus.PROCESSING)
            }

            updatingYTDL = true

            try {
                val channel = if (c.isNullOrBlank()) sharedPreferences.getString("ytdlp_source", "stable") else c

                val response = when(channel) {
                    "stable", "nightly", "master" -> {
                        val res = RuntimeManager.getInstance().updateYTDL(context, channelMap[channel]!!)
                        if (res != RuntimeManager.UpdateStatus.DONE) {
                            YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE)
                        }else {
                            val version = RuntimeManager.getInstance().version(context)
                            YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, "Updated yt-dlp to ${channel}@${version}")
                        }
                    }
                    else -> {
                        val request = YTDLRequest(emptyList())
                        request.addOption("--update-to", "$channel")

                        val res = RuntimeManager.getInstance().execute(request)
                        val out = res.out.lines().last { it.isNotBlank() }

                        when {
                            out.contains("ERROR") -> YTDLPUpdateResponse(YTDLPUpdateStatus.ERROR, out)
                            out.contains("yt-dlp is up to date") -> YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE, out)
                            else -> YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, out)
                        }
                    }
                }

                if (response.status == YTDLPUpdateStatus.DONE || response.status == YTDLPUpdateStatus.ALREADY_UP_TO_DATE) {
                    sharedPreferences.edit().putLong(YTDLP_LAST_CHECK_KEY, System.currentTimeMillis()).apply()
                }
                response
            } finally {
                updatingYTDL = false
            }
        }

    @SuppressLint("Range", "UnspecifiedRegisterReceiverFlag")
    suspend fun downloadReleaseApk(release: GithubRelease, onProgress: (Long) -> Unit) : Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val releaseVersion = selectApkAsset(release.assets)
                    ?: return@withContext Result.failure(Error(context.getString(R.string.errored)))
                File(FileUtil.getDefaultApksPath()).mkdirs()
                val tempApk = File(FileUtil.getDefaultApksPath(), "${releaseVersion.browser_download_url.split("/").last()}")

                //download
                val request = Request.Builder()
                    .url(releaseVersion.browser_download_url)
                    .build()

                val response = sharedClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Throwable(response.body.string()))
                }

                val body = response.body
                val totalBytes = body.contentLength()
                var bytesDownloaded = 0L

                body.byteStream().use { inputStream ->
                    tempApk.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192) // 8KB buffer
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) throw CancellationException("Download cancelled by user")

                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Calculate percentage
                            val progress = if (totalBytes > 0L) (bytesDownloaded * 100) / totalBytes else 0L
                            onProgress(progress)
                        }
                    }
                }

                Result.success(tempApk)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/ezzatemam1982-hue/EZTube/releases"
        const val APP_LAST_CHECK_KEY = "app_last_update_check"
        const val YTDLP_LAST_CHECK_KEY = "ytdlp_last_update_check"
        var updatingYTDL = false
        var updatingApp = false

        internal fun compareVersionTags(a: String, b: String): Int {
            fun parts(tag: String) = tag.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-')
                .split('.')
                .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val x = parts(a)
            val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val cmp = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
                if (cmp != 0) return cmp
            }
            return 0
        }

        internal fun selectApkAsset(
            assets: List<GithubReleaseAsset>,
            abi: String? = Build.SUPPORTED_ABIS.firstOrNull(),
        ): GithubReleaseAsset? {
            val apks = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    !it.name.contains("foss", ignoreCase = true) &&
                    !it.name.contains("izzy", ignoreCase = true)
            }
            return abi?.takeIf { it.isNotBlank() }?.let { targetAbi ->
                apks.firstOrNull { it.name.contains(targetAbi, ignoreCase = true) }
            } ?: apks.firstOrNull {
                it.name.contains("universal", ignoreCase = true) ||
                    it.name.equals("EZTube.apk", ignoreCase = true) ||
                    it.name.equals("ClipFlow.apk", ignoreCase = true)
            } ?: apks.firstOrNull()
        }
    }
}