package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.util.DragSelectTouchListener
import com.deniscerri.ytdl.util.Extensions.HideThumbnailFlag
import com.deniscerri.ytdl.util.Extensions.hideThumbnailFlag
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


class HistoryPaginatedAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : PagingDataAdapter<HistoryItem, HistoryPaginatedAdapter.ViewHolder>(
    DIFF_CALLBACK
), DragSelectTouchListener.Selectable {
    val checkedItems: MutableSet<Long>
    var inverted: Boolean
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences
    var useDownloadCardDesign: Boolean = false
    private val hideThumbFlag: HideThumbnailFlag
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filePresenceCache = LruCache<Long, Pair<List<String>, Boolean>>(256)
    private val dateFormat by lazy {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault())
    }
    private val desaturatedFilter by lazy {
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val transparentTint by lazy {
        ContextCompat.getColorStateList(activity, android.R.color.transparent)!!
    }
    private val surfaceTint by lazy {
        MaterialColors.getColorStateList(activity, R.attr.colorSurface, transparentTint)
    }
    private val primaryContainerTint by lazy {
        MaterialColors.getColorStateList(activity, R.attr.colorPrimaryContainer, transparentTint)
    }

    init {
        checkedItems = mutableSetOf()
        this.inverted = false
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        useDownloadCardDesign = sharedPreferences.getBoolean("history_use_download_card_design", false)
        hideThumbFlag = sharedPreferences.hideThumbnailFlag("downloads")
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView
        var filesPresent: Boolean = true

        init {
            cardView = itemView.findViewById(R.id.download_card_view)
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryPaginatedAdapter.ViewHolder {
        val layoutRes = when (viewType) {
            TYPE_STANDARD_MULTIPLE -> R.layout.history_card_multiple
            TYPE_DOWNLOAD_CARD_SINGLE -> R.layout.download_card
            TYPE_DOWNLOAD_CARD_MULTIPLE -> R.layout.download_card_multiple
            else -> R.layout.history_card
        }

        val cardView = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position) ?: return TYPE_STANDARD_SINGLE
        if (useDownloadCardDesign) {
            return if (item.downloadPath.size == 1) {
                TYPE_DOWNLOAD_CARD_SINGLE
            } else {
                TYPE_DOWNLOAD_CARD_MULTIPLE
            }
        }

        return if (item.downloadPath.size == 1) {
            TYPE_STANDARD_SINGLE
        } else {
            TYPE_STANDARD_MULTIPLE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun toggleCardDesign() {
        useDownloadCardDesign = !useDownloadCardDesign
        sharedPreferences.edit(commit = true) {
            putBoolean("history_use_download_card_design", useDownloadCardDesign)
        }
        notifyDataSetChanged()
    }

    private fun applyTypeButtonIcon(btn: MaterialButton, type: DownloadType, filesPresent: Boolean) {
        if (type == DownloadType.audio) {
            btn.setIconResource(if (filesPresent) R.drawable.ic_music_downloaded else R.drawable.ic_music)
        } else if (type == DownloadType.video) {
            btn.setIconResource(if (filesPresent) R.drawable.ic_video_downloaded else R.drawable.ic_video)
        } else {
            btn.setIconResource(if (filesPresent) R.drawable.ic_terminal else R.drawable.baseline_code_off_24)
        }
    }

    private fun applyTypeFabIcon(btn: FloatingActionButton, type: DownloadType, filesPresent: Boolean) {
        if (type == DownloadType.audio) {
            btn.setImageResource(if (filesPresent) R.drawable.ic_music_downloaded else R.drawable.ic_music)
        } else if (type == DownloadType.video) {
            btn.setImageResource(if (filesPresent) R.drawable.ic_video_downloaded else R.drawable.ic_video)
        } else {
            btn.setImageResource(if (filesPresent) R.drawable.ic_terminal else R.drawable.baseline_code_off_24)
        }
    }

    private fun resolveFilePresence(holder: ViewHolder, item: HistoryItem, onResolved: (Boolean) -> Unit) {
        val cached = synchronized(filePresenceCache) {
            filePresenceCache.get(item.id)?.takeIf { it.first == item.downloadPath }?.second
        }
        if (cached != null) {
            holder.filesPresent = cached
            onResolved(cached)
            return
        }
        holder.filesPresent = true
        onResolved(true)
        val expectedTag = item.id.toString()
        val paths = item.downloadPath
        ioScope.launch {
            val present = !paths.all { !File(it).exists() && it.isNotBlank() }
            synchronized(filePresenceCache) {
                filePresenceCache.put(item.id, paths to present)
            }
            if (!present) {
                withContext(Dispatchers.Main.immediate) {
                    if (holder.itemView.tag == expectedTag) {
                        holder.filesPresent = false
                        onResolved(false)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val item = getItem(position) ?: return
        val card = holder.cardView
        val itemTag = item.id.toString()
        holder.itemView.tag = itemTag
        card.tag = itemTag
        card.popup()

        val viewType = getItemViewType(position)
        if (viewType == TYPE_STANDARD_SINGLE || viewType == TYPE_STANDARD_MULTIPLE) {
            val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

            // THUMBNAIL ----------------------------------
            thumbnail.post { thumbnail.loadThumbnail(hideThumbFlag.hidden, item.thumb) }

            // TITLE  ----------------------------------
            val itemTitle = card.findViewById<TextView>(R.id.downloads_title)
            var title = item.title.ifEmpty { item.url }
            if (title.length > 100) {
                title = title.substring(0, 40) + "..."
            }
            itemTitle.text = title

            // Bottom Info ----------------------------------
            val author = card.findViewById<TextView>(R.id.downloads_info_bottom)
            author.text = item.author

            val length = card.findViewById<TextView>(R.id.length)
            length.text = if(item.downloadPath.size == 1) item.duration else ""


            // TIME DOWNLOADED  ----------------------------------
            val datetime = card.findViewById<TextView>(R.id.downloads_info_time)
            datetime.text = dateFormat.format(item.time * 1000L)

            // BUTTON ----------------------------------
            val btn = card.findViewById<FloatingActionButton>(R.id.downloads_download_button_type)
            if (btn.hasOnClickListeners()) btn.setOnClickListener(null)

            resolveFilePresence(holder, item) { filesPresent ->
                if (!filesPresent) {
                    thumbnail.colorFilter = desaturatedFilter
                    thumbnail.alpha = 0.7f
                    btn.backgroundTintList = surfaceTint
                } else {
                    thumbnail.alpha = 1f
                    thumbnail.colorFilter = null
                    btn.backgroundTintList = primaryContainerTint
                }
                applyTypeFabIcon(btn, item.type, filesPresent)
                btn.isClickable = filesPresent
            }

            if ((checkedItems.contains(item.id) && !inverted) || (!checkedItems.contains(item.id) && inverted)) {
                card.isChecked = true
                card.strokeWidth = 5
            } else {
                card.isChecked = false
                card.strokeWidth = 0
            }
            card.setOnLongClickListener {
                checkCard(card, item.id, position)
                true
            }
            card.setOnClickListener {
                if (checkedItems.size > 0 || inverted) {
                    checkCard(card, item.id, position)
                } else {
                    onItemClickListener.onCardClick(item.id, holder.filesPresent)
                }
            }

            btn.setOnClickListener {
                onItemClickListener.onButtonClick(item.id, holder.filesPresent)
            }
            card.findViewById<View>(R.id.history_copy_link_button)?.apply {
                isVisible = item.url.isNotBlank()
                setOnClickListener {
                    com.deniscerri.ytdl.util.UiUtil.copyLinkToast(activity, item.url)
                }
            }
        } else {
            val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)

            // THUMBNAIL ----------------------------------
            thumbnail.post { thumbnail.loadThumbnail(hideThumbFlag.hidden, item.thumb) }

            // TITLE  ----------------------------------
            val itemTitle = card.findViewById<TextView>(R.id.title)
            var title = item.title.ifEmpty { item.url }
            if (title.length > 100) {
                title = title.substring(0, 40) + "..."
            }
            itemTitle.text = title

            val length = card.findViewById<TextView>(R.id.duration)
            length.text = if(item.downloadPath.size == 1) item.duration else ""
            length.isVisible = length.text.isNotBlank()

            val author = card.findViewById<TextView>(R.id.download_type)
            author.text = item.author
            author.isVisible = item.author.isNotBlank()

            val formatNote = card.findViewById<TextView>(R.id.format_note)
            formatNote.visibility = View.GONE

            val codec = card.findViewById<TextView>(R.id.codec)
            codec.visibility = View.GONE

            // TIME DOWNLOADED  ----------------------------------
            val datetime = card.findViewById<TextView>(R.id.file_size)
            datetime.text = dateFormat.format(item.time * 1000L)

            // BUTTON ----------------------------------
            val btn = card.findViewById<MaterialButton>(R.id.action_button)
            if (btn.hasOnClickListeners()) btn.setOnClickListener(null)

            resolveFilePresence(holder, item) { filesPresent ->
                if (!filesPresent) {
                    thumbnail.colorFilter = desaturatedFilter
                    thumbnail.alpha = 0.7f
                } else {
                    thumbnail.alpha = 1f
                    thumbnail.colorFilter = null
                }
                btn.backgroundTintList = surfaceTint
                applyTypeButtonIcon(btn, item.type, filesPresent)
                btn.isClickable = filesPresent
            }

            if ((checkedItems.contains(item.id) && !inverted) || (!checkedItems.contains(item.id) && inverted)) {
                card.isChecked = true
                card.strokeWidth = 5
            } else {
                card.isChecked = false
                card.strokeWidth = 0
            }
            card.setOnLongClickListener {
                checkCard(card, item.id, position)
                true
            }
            card.setOnClickListener {
                if (checkedItems.size > 0 || inverted) {
                    checkCard(card, item.id, position)
                } else {
                    onItemClickListener.onCardClick(item.id, holder.filesPresent)
                }
            }

            btn.setOnClickListener {
                onItemClickListener.onButtonClick(item.id, holder.filesPresent)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearCheckedItems() {
        inverted = false
        checkedItems.clear()
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkAll() {
        checkedItems.clear()
        inverted = true
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkMultipleItems(list: List<Long>){
        checkedItems.clear()
        inverted = false
        checkedItems.addAll(list)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invertSelected() {
        inverted = !inverted
        notifyDataSetChanged()
    }

    fun getSelectedObjectsCount(totalSize: Int) : Int{
        return if (inverted){
            totalSize - checkedItems.size
        }else{
            checkedItems.size
        }
    }

    private fun checkCard(card: MaterialCardView, itemID: Long, position: Int) {
        if (card.isChecked) {
            card.strokeWidth = 0
            if (inverted) checkedItems.add(itemID)
            else checkedItems.remove(itemID)
        } else {
            card.strokeWidth = 5
            if (inverted) checkedItems.remove(itemID)
            else checkedItems.add(itemID)
        }
        card.isChecked = !card.isChecked
        onItemClickListener.onCardSelect(card.isChecked, position)
    }

    override fun isItemSelected(position: Int): Boolean {
        val item = (if (position in 0 until itemCount) peek(position) else null) ?: return false
        return checkedItems.contains(item.id) != inverted
    }

    override fun setItemSelected(position: Int, selected: Boolean) {
        val item = (if (position in 0 until itemCount) peek(position) else null) ?: return
        if (isItemSelected(position) == selected) return
        if (selected != inverted) checkedItems.add(item.id) else checkedItems.remove(item.id)
        // the payload skips the change animation so cards don't flash while dragging
        notifyItemChanged(position, "selection")
        onItemClickListener.onCardSelect(selected, position)
    }

    interface OnItemClickListener {
        fun onButtonClick(itemID: Long, filePresent: Boolean)
        fun onCardClick(itemID: Long, filePresent: Boolean)
        fun onCardSelect(isChecked: Boolean, position: Int)
    }

    companion object {
        private const val TYPE_STANDARD_SINGLE = 0
        private const val TYPE_STANDARD_MULTIPLE = 1
        private const val TYPE_DOWNLOAD_CARD_SINGLE = 2
        private const val TYPE_DOWNLOAD_CARD_MULTIPLE = 3

        private val DIFF_CALLBACK: DiffUtil.ItemCallback<HistoryItem> = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem.time == newItem.time
            }
        }
    }
}