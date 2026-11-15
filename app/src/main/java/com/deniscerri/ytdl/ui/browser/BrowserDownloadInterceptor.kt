package com.deniscerri.ytdl.ui.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.downloadcard.DownloadBottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.Locale

data class DetectedMedia(
    val url: String,
    val extension: String,
    val referer: String,
    val pageTitle: String,
    val detectedAt: Long = System.currentTimeMillis(),
)

/**
 * Handles URL classification, passive stream recording, direct HTTP file handoff via
 * Android [DownloadManager], and authenticated yt-dlp handoff into EZTube's
 * [DownloadBottomSheetDialog] without touching core download engine files.
 */
object BrowserDownloadInterceptor {

    private val DIRECT_DOWNLOAD_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "mp3", "m4a", "zip", "rar", "apk"
    )

    private val PASSIVE_STREAM_EXTENSIONS = setOf(
        "mp4", "m3u8", "mpd", "webm", "mkv", "mp3"
    )

    private val ARCHIVE_OR_BINARY_EXTENSIONS = setOf(
        "zip", "rar", "apk"
    )

    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a")

    private val UTF8_FILENAME_REGEX = Regex("filename\\*=(?:UTF-8|utf-8)''([^;\\r\\n]+)")

    private const val DEDUP_WINDOW_MS = 2500L
    private const val MAX_CAPTURED_STREAMS = 60

    private val recentEnqueueLock = Any()
    private val recentEnqueuedUrls = HashMap<String, Long>()

    fun extractExtension(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val path = runCatching { Uri.parse(url).path }.getOrNull()
            ?: url.substringBefore('#').substringBefore('?')
        val lastSegment = path.substringAfterLast('/', "")
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex < 0 || dotIndex == lastSegment.lastIndex) return ""
        return lastSegment.substring(dotIndex + 1).lowercase(Locale.US)
    }

    fun isHttpUrl(url: String?): Boolean {
        val trimmed = url?.trim() ?: return false
        val afterScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
            else -> return false
        }
        return afterScheme.isNotEmpty() && afterScheme.first() != '/'
    }

    fun isDirectDownloadUrl(url: String?): Boolean {
        if (!isHttpUrl(url)) return false
        return extractExtension(url) in DIRECT_DOWNLOAD_EXTENSIONS
    }

    fun isArchiveOrBinaryUrl(url: String?): Boolean {
        if (!isHttpUrl(url)) return false
        return extractExtension(url) in ARCHIVE_OR_BINARY_EXTENSIONS
    }

    fun isPassiveStreamUrl(url: String?): Boolean {
        if (!isHttpUrl(url)) return false
        return extractExtension(url) in PASSIVE_STREAM_EXTENSIONS
    }

    fun isMediaMime(mimeType: String?): Boolean {
        val m = mimeType?.lowercase(Locale.US) ?: return false
        return m.startsWith("video/") || m.startsWith("audio/") ||
            m.contains("mpegurl") || m.contains("dash+xml")
    }

    fun guessSafeFileName(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
    ): String {
        val utf8Name = contentDisposition
            ?.let { UTF8_FILENAME_REGEX.find(it)?.groupValues?.getOrNull(1) }
            ?.let { raw -> runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

        val rawName = utf8Name
            ?: URLUtil.guessFileName(url, contentDisposition, mimeType)

        val cleaned = rawName
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .take(180)

        return cleaned.ifBlank { "download_${System.currentTimeMillis()}" }
    }

    sealed class DirectDownloadResult {
        data class Started(val fileName: String) : DirectDownloadResult()
        data object DuplicateSkipped : DirectDownloadResult()
        data object UnsupportedScheme : DirectDownloadResult()
        data class Failed(val reason: String) : DirectDownloadResult()
    }

    fun enqueueDirectDownload(
        context: Context,
        url: String,
        userAgent: String?,
        referer: String?,
        contentDisposition: String? = null,
        mimeType: String? = null,
    ): DirectDownloadResult {
        if (!isHttpUrl(url)) {
            return DirectDownloadResult.UnsupportedScheme
        }

        val now = System.currentTimeMillis()
        synchronized(recentEnqueueLock) {
            val prev = recentEnqueuedUrls[url]
            if (prev != null && now - prev < DEDUP_WINDOW_MS) {
                return DirectDownloadResult.DuplicateSkipped
            }
            recentEnqueuedUrls[url] = now
            if (recentEnqueuedUrls.size > 32) {
                recentEnqueuedUrls.entries.removeAll { now - it.value > DEDUP_WINDOW_MS * 4 }
            }
        }

        val fileName = guessSafeFileName(url, contentDisposition, mimeType)
        val ext = extractExtension(fileName).ifEmpty { extractExtension(url) }
        val resolvedMime = mimeType?.takeIf {
            it.isNotBlank() &&
                !it.equals("application/octet-stream", ignoreCase = true) &&
                !it.equals("application/force-download", ignoreCase = true)
        } ?: ext.takeIf { it.isNotBlank() }?.let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
        }

        val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

        return runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription(runCatching { Uri.parse(url).host }.getOrNull() ?: url)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                resolvedMime?.let { setMimeType(it) }
                addSanitizedHeader("Cookie", cookies)
                addSanitizedHeader("User-Agent", userAgent)
                addSanitizedHeader("Referer", referer)
                try {
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "ClipFlow/$fileName"
                    )
                } catch (_: Exception) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
            }
            dm.enqueue(request)
            DirectDownloadResult.Started(fileName)
        }.getOrElse { err ->
            DirectDownloadResult.Failed(err.message ?: "Download failed")
        }
    }

    fun buildYtDlpHeaderCommands(
        cookie: String?,
        userAgent: String?,
        referer: String?,
    ): String {
        val parts = mutableListOf<String>()
        appendHeaderOption(parts, "Cookie", cookie)
        appendHeaderOption(parts, "Referer", referer)
        appendHeaderOption(parts, "User-Agent", userAgent)
        return parts.joinToString(" ")
    }

    /**
     * Hands off a stream/page/link to EZTube's [DownloadBottomSheetDialog] with cookie,
     * User-Agent, and Referer headers attached via `downloadItem.extraCommands`,
     * leaving [DownloadWorker] and [YTDLPUtil] untouched.
     */
    fun openInEzTubeDownloader(
        activity: FragmentActivity,
        url: String,
        title: String? = null,
        cookie: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        isDirectOrCapturedStream: Boolean = false,
    ) {
        if (!isHttpUrl(url)) return

        val resolvedCookie = cookie?.takeIf { it.isNotBlank() }
            ?: runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

        val downloadViewModel = ViewModelProvider(activity)[DownloadViewModel::class.java]
        val downloadCardViewModel = ViewModelProvider(activity)[DownloadCardViewModel::class.java]
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)

        activity.lifecycleScope.launch {
            val (resultItem, downloadItem) = withContext(Dispatchers.IO) {
                val baseResult = downloadViewModel.createEmptyResultItem(url)
                val ext = extractExtension(url)
                val host = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
                val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (isDirectOrCapturedStream) guessSafeFileName(url, null, null) else host
                val enrichedResult = baseResult.copy(
                    title = resolvedTitle,
                    website = host,
                    author = host,
                )

                val preferredName = prefs.getString("preferred_download_type", "video") ?: "video"
                val preferredType = runCatching { DownloadType.valueOf(preferredName) }
                    .getOrDefault(DownloadType.video)
                val targetType = if (ext in AUDIO_EXTENSIONS) {
                    DownloadType.audio
                } else {
                    downloadViewModel.getDownloadType(preferredType, url)
                }

                val item: DownloadItem = downloadViewModel.createDownloadItemFromResult(
                    result = enrichedResult,
                    url = url,
                    givenType = targetType,
                )

                val headerArgs = buildYtDlpHeaderCommands(
                    cookie = resolvedCookie,
                    userAgent = userAgent,
                    referer = referer,
                )
                if (headerArgs.isNotBlank()) {
                    item.extraCommands = listOf(item.extraCommands, headerArgs)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                item.title = resolvedTitle
                item.website = host

                enrichedResult to item
            }

            if (activity.isFinishing || activity.isDestroyed || activity.supportFragmentManager.isStateSaved) {
                return@launch
            }

            downloadCardViewModel.setResultItem(resultItem)
            downloadCardViewModel.setDownloadItem(downloadItem)

            val existing = activity.supportFragmentManager.findFragmentByTag("browserDownloadSheet")
            if (existing is DownloadBottomSheetDialog) {
                existing.dismissAllowingStateLoss()
            }

            DownloadBottomSheetDialog().apply {
                arguments = bundleOf(
                    "type" to downloadItem.type,
                    "disableUpdateData" to isDirectOrCapturedStream,
                )
            }.show(activity.supportFragmentManager, "browserDownloadSheet")
        }
    }

    class StreamRegistry {
        private val lock = Any()
        private val items = LinkedHashMap<String, DetectedMedia>()

        fun record(url: String, referer: String, pageTitle: String): Boolean {
            if (!isPassiveStreamUrl(url) || AdBlocker.isAdUrl(url)) return false
            val ext = extractExtension(url)
            synchronized(lock) {
                val isNew = !items.containsKey(url)
                if (items.size >= MAX_CAPTURED_STREAMS && isNew) {
                    val oldestKey = items.keys.firstOrNull()
                    if (oldestKey != null) items.remove(oldestKey)
                }
                items[url] = DetectedMedia(
                    url = url,
                    extension = ext,
                    referer = referer,
                    pageTitle = pageTitle,
                )
                return isNew
            }
        }

        fun snapshot(): List<DetectedMedia> = synchronized(lock) {
            items.values.toList().asReversed()
        }

        fun count(): Int = synchronized(lock) { items.size }

        fun clear() = synchronized(lock) { items.clear() }
    }

    private fun DownloadManager.Request.addSanitizedHeader(name: String, rawValue: String?) {
        val clean = sanitizeHeaderValue(rawValue) ?: return
        addRequestHeader(name, clean)
    }

    private fun appendHeaderOption(target: MutableList<String>, name: String, rawValue: String?) {
        val clean = sanitizeHeaderValue(rawValue) ?: return
        val escaped = clean
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        target.add("--add-header \"$name:$escaped\"")
    }

    private fun sanitizeHeaderValue(rawValue: String?): String? =
        rawValue
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?.replace("\u0000", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
