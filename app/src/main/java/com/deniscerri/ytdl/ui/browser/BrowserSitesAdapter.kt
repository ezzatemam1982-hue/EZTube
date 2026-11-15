package com.deniscerri.ytdl.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.google.android.material.button.MaterialButton

class BrowserSitesAdapter(
    private val onSiteClick: (BrowserSite) -> Unit,
    private val onRemoveClick: (BrowserSite) -> Unit,
) : ListAdapter<BrowserSite, BrowserSitesAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.site_icon)
        val title: TextView = itemView.findViewById(R.id.site_title)
        val badge: TextView = itemView.findViewById(R.id.site_badge)
        val url: TextView = itemView.findViewById(R.id.site_url)
        val removeBtn: MaterialButton = itemView.findViewById(R.id.site_remove_btn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_browser_site, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        holder.title.text = item.title
        holder.url.text = item.url
        holder.badge.text = if (item.isSaved) {
            context.getString(R.string.browser_saved_badge)
        } else {
            context.getString(R.string.browser_recent_badge)
        }
        holder.icon.setImageResource(
            if (item.isSaved) R.drawable.ic_browser_bookmark_filled else R.drawable.ic_browser_globe
        )
        holder.itemView.setOnClickListener { onSiteClick(item) }
        holder.removeBtn.setOnClickListener { onRemoveClick(item) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BrowserSite>() {
            override fun areItemsTheSame(oldItem: BrowserSite, newItem: BrowserSite): Boolean =
                oldItem.url == newItem.url && oldItem.isSaved == newItem.isSaved

            override fun areContentsTheSame(oldItem: BrowserSite, newItem: BrowserSite): Boolean =
                oldItem == newItem
        }
    }
}
