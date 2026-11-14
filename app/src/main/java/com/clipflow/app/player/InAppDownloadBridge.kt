package com.clipflow.app.player

import android.content.Context
import android.content.ContextWrapper
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.downloadcard.DownloadBottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct in-process bridge between ClipFlow's Compose streaming player (regular video & Shorts)
 * and ClipFlow's internal [DownloadBottomSheetDialog] + Downloader Hub.
 * Fires zero external intents; everything runs inside the unified [MainActivity] process.
 */
object InAppDownloadBridge {

    /**
     * Opens ClipFlow's [DownloadBottomSheetDialog] directly over the active player for the given YouTube video or Short.
     */
    fun openDownloadSheet(
        context: Context,
        videoId: String,
        title: String? = null,
        author: String? = null,
        thumbnail: String? = null,
        durationSec: Long = 0L,
        isShorts: Boolean = false,
    ) {
        if (videoId.isBlank()) return
        val activity = context.findBaseActivity() ?: return
        val canonicalUrl = if (isShorts) {
            "https://www.youtube.com/shorts/$videoId"
        } else {
            "https://www.youtube.com/watch?v=$videoId"
        }

        val downloadViewModel = ViewModelProvider(activity)[DownloadViewModel::class.java]
        val resultViewModel = ViewModelProvider(activity)[ResultViewModel::class.java]
        val downloadCardViewModel = ViewModelProvider(activity)[DownloadCardViewModel::class.java]

        activity.lifecycleScope.launch {
            val result: ResultItem = withContext(Dispatchers.IO) {
                val existing = runCatching { resultViewModel.getAllByURL(canonicalUrl) }.getOrNull().orEmpty()
                if (existing.size == 1) {
                    existing.first()
                } else {
                    val base = downloadViewModel.createEmptyResultItem(canonicalUrl)
                    val resolvedThumb = thumbnail?.takeIf { it.isNotBlank() }
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    val resolvedDuration = if (durationSec > 0L) {
                        formatDuration(durationSec * 1000L)
                    } else {
                        base.duration
                    }
                    base.copy(
                        title = title?.takeIf { it.isNotBlank() } ?: base.title,
                        author = author?.takeIf { it.isNotBlank() } ?: base.author,
                        thumb = resolvedThumb,
                        duration = resolvedDuration,
                    )
                }
            }

            val downloadType = downloadViewModel.getDownloadType(url = result.url)
            downloadCardViewModel.setResultItem(result)
            downloadCardViewModel.setDownloadItem(null)

            val existingSheet = activity.supportFragmentManager.findFragmentByTag("downloadBottomSheet")
            if (existingSheet is DownloadBottomSheetDialog) {
                existingSheet.dismissAllowingStateLoss()
            }

            DownloadBottomSheetDialog().apply {
                arguments = bundleOf("type" to downloadType)
            }.show(activity.supportFragmentManager, "downloadBottomSheet")
        }
    }

    /**
     * Switches from the streaming UI to ClipFlow's Downloader Hub (active queue, downloads history, settings).
     */
    fun openDownloaderHub(context: Context, destination: String? = null) {
        val mainActivity = context.findMainActivity() ?: return
        mainActivity.openDownloaderHub(destination)
    }

    /**
     * Opens ClipFlow's Downloader Settings screen ([com.deniscerri.ytdl.ui.more.settings.SettingsActivity])
     * directly so pressing Back returns to Streaming Settings.
     */
    fun openDownloaderSettings(context: Context) {
        val activity = context.findBaseActivity()
        if (activity != null) {
            activity.startActivity(
                android.content.Intent(activity, com.deniscerri.ytdl.ui.more.settings.SettingsActivity::class.java)
            )
        } else {
            context.startActivity(
                android.content.Intent(context, com.deniscerri.ytdl.ui.more.settings.SettingsActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private tailrec fun Context.findBaseActivity(): BaseActivity? = when (this) {
        is BaseActivity -> this
        is ContextWrapper -> baseContext.findBaseActivity()
        else -> null
    }

    private tailrec fun Context.findMainActivity(): MainActivity? = when (this) {
        is MainActivity -> this
        is ContextWrapper -> baseContext.findMainActivity()
        else -> null
    }
}
