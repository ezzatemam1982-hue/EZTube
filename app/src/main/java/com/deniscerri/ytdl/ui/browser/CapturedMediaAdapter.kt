package com.deniscerri.ytdl.ui.browser

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.google.android.material.button.MaterialButton

class CapturedMediaAdapter(
    private val onDirectDownload: (DetectedMedia) -> Unit,
    private val onOpenInEzTube: (DetectedMedia) -> Unit,
) : ListAdapter<DetectedMedia, CapturedMediaAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val extBadge: TextView = itemView.findViewById(R.id.tv_media_ext_badge)
        val name: TextView = itemView.findViewById(R.id.tv_media_name)
        val url: TextView = itemView.findViewById(R.id.tv_media_url)
        val directBtn: MaterialButton = itemView.findViewById(R.id.btn_media_direct)
        val ezTubeBtn: MaterialButton = itemView.findViewById(R.id.btn_media_eztube)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_captured_media, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val ext = item.extension.uppercase()
        holder.extBadge.text = if (ext == "M3U8") "HLS" else if (ext == "MPD") "DASH" else ext

        val pathName = runCatching { Uri.parse(item.url).lastPathSegment }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        val fallbackTitle = item.pageTitle.takeIf { it.isNotBlank() }
            ?: runCatching { Uri.parse(item.url).host }.getOrNull()
            ?: item.url
        holder.name.text = pathName ?: fallbackTitle
        holder.url.text = item.url

        // HLS / DASH manifests must go through yt-dlp rather than raw DownloadManager.
        val isManifest = item.extension == "m3u8" || item.extension == "mpd"
        holder.directBtn.isVisible = !isManifest
        holder.directBtn.setOnClickListener { onDirectDownload(item) }
        holder.ezTubeBtn.setOnClickListener { onOpenInEzTube(item) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DetectedMedia>() {
            override fun areItemsTheSame(oldItem: DetectedMedia, newItem: DetectedMedia): Boolean =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: DetectedMedia, newItem: DetectedMedia): Boolean =
                oldItem == newItem
        }
    }
}
