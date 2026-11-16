package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * Preference whose summary displays filesystem paths strictly LTR on a single line so Arabic/RTL
 * layouts never wrap trailing segments (like "Backups") awkwardly onto another line.
 */
class LtrPathPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView ?: return
        summaryView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        summaryView.textDirection = View.TEXT_DIRECTION_LTR
        summaryView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        summaryView.maxLines = 1
        summaryView.ellipsize = TextUtils.TruncateAt.START
    }
}
