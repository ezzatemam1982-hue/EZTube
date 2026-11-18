package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder


abstract class BaseSettingsFragment : PreferenceFragmentCompat(), SettingHost {
    abstract val title: Int

    private lateinit var installLauncher: ActivityResultLauncher<Intent>

    override fun findPref(key: String) = findPreference<Preference>(key)
    @SuppressLint("NotifyDataSetChanged")
    override fun refreshUI() {
        listView.adapter?.notifyDataSetChanged()
    }
    override fun getHostContext() = requireActivity()
    override fun getAppInstallLauncher() = installLauncher
    override val activityResultDelegate = PreferenceActivityResultDelegate(this)
    override fun requestGetParentFragmentManager() = parentFragmentManager
    override fun requestRecreateActivity() = requireActivity().recreate()
    override fun requestNavigate(id: Int) = findNavController().navigate(id)
    override val hostViewModelStoreOwner by lazy {
        this
    }
    override val hostLifecycleOwner by lazy {
        this
    }
    override val hostView by lazy {
        view
    }
    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }

    fun getPreferences(p: Preference, list: MutableList<Preference>) : List<Preference> {
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            val pCount: Int = pGroup.preferenceCount
            for (i in 0 until pCount) {
                getPreferences(pGroup.getPreference(i), list) // recursive call
            }
        } else {
            list.add(p)
        }
        return list
    }

    fun resetPreferences(editor: SharedPreferences.Editor, key: Int) {
        getPreferences(preferenceScreen, mutableListOf()).forEach {
            editor.remove(it.key)
        }
        editor.apply()
        PreferenceManager.setDefaultValues(requireActivity().applicationContext, key, true)
    }

    //Thanks libretube
    override fun onDisplayPreferenceDialog(preference: Preference) {
        val shownCustomDialog = DefaultPreferenceActions.onPreferenceDisplayDialog(requireActivity(), preference) {}
        if (!shownCustomDialog) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installLauncher = ApkInstallUtil.registerInstallLauncher(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setBackgroundColor(0xFF000000.toInt())
        listView.setBackgroundColor(0xFF000000.toInt())

        listView.clipToPadding = false
        val initialBottomPadding = listView.paddingBottom
        val extraBottom = (80 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val navBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                initialBottomPadding + navBars.bottom + extraBottom
            )
            insets
        }

        listView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val marginH = (16 * resources.displayMetrics.density).toInt()
            private val marginV = (4 * resources.displayMetrics.density).toInt()

            override fun getItemOffsets(
                outRect: Rect,
                v: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val pos = parent.getChildAdapterPosition(v)
                if (pos != RecyclerView.NO_POSITION) {
                    val adapter = parent.adapter as? PreferenceGroupAdapter
                    val pref = adapter?.getItem(pos)
                    if (pref != null && pref !is PreferenceCategory && v.findViewById<View>(R.id.status_card) == null) {
                        outRect.set(marginH, marginV, marginH, marginV)
                    }
                }
            }
        })

        listView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(v: View) {
                val holder = listView.getChildViewHolder(v)
                val pos = holder.bindingAdapterPosition
                val adapter = listView.adapter as? PreferenceGroupAdapter
                val pref = if (pos != RecyclerView.NO_POSITION) adapter?.getItem(pos) else null

                if (pref is PreferenceCategory) {
                    val catTitle = v.findViewById<TextView>(android.R.id.title)
                    catTitle?.apply {
                        val colorAttr = TypedValue()
                        context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, colorAttr, true)
                        setTextColor(colorAttr.data)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                } else if (pref != null && v.findViewById<View>(R.id.status_card) == null) {
                    v.setBackgroundResource(R.drawable.bg_settings_card)
                }

                val iconView = v.findViewById<ImageView>(android.R.id.icon)
                val iconFrame = v.findViewById<View>(android.R.id.icon_frame)
                if (iconView != null && iconView.visibility == View.VISIBLE && iconView.drawable != null) {
                    val density = resources.displayMetrics.density
                    val iconSize = (38 * density).toInt()
                    val iconPadding = (8 * density).toInt()
                    val spacing = (16 * density).toInt()
                    val containerWidth = (38 * density).toInt()

                    iconView.layoutParams = iconView.layoutParams.apply {
                        width = iconSize
                        height = iconSize
                    }
                    iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                    val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF262626.toInt())
                        cornerRadius = 10 * density
                    }
                    iconView.background = bgDrawable
                    val colorAttr = TypedValue()
                    v.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, colorAttr, true)
                    iconView.imageTintList = ColorStateList.valueOf(colorAttr.data)

                    // Ensure uniform 38dp container width and 16dp horizontal spacing across all cards
                    // so SeekBarPreference (Concurrent fragments/downloads) aligns with List/Switch cards
                    if (iconFrame != null) {
                        iconFrame.minimumWidth = containerWidth
                        (iconFrame.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                            lp.width = containerWidth
                            lp.marginEnd = spacing
                            iconFrame.layoutParams = lp
                        }
                        iconFrame.setPadding(0, 0, 0, 0)
                    } else {
                        (iconView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                            lp.marginEnd = spacing
                            iconView.layoutParams = lp
                        }
                    }
                }

                val titleView = v.findViewById<TextView>(android.R.id.title)
                if (titleView != null) {
                    titleView.isSingleLine = false
                    titleView.maxLines = 2
                    titleView.setHorizontallyScrolling(false)
                    titleView.ellipsize = TextUtils.TruncateAt.END
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        titleView,
                        11,
                        15,
                        1,
                        TypedValue.COMPLEX_UNIT_SP
                    )
                }

                val seekBar = v.findViewById<android.widget.SeekBar>(androidx.preference.R.id.seekbar)
                if (seekBar != null) {
                    val colorAttr = TypedValue()
                    v.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, colorAttr, true)
                    val primaryColor = ColorStateList.valueOf(colorAttr.data)
                    seekBar.progressTintList = primaryColor
                    seekBar.thumbTintList = primaryColor
                }

                val summaryView = v.findViewById<TextView>(android.R.id.summary)
                val summaryText = summaryView?.text
                val hasDescription = !summaryText.isNullOrBlank()
                val isDynamicPathOrValue = summaryText?.let {
                    it.contains("/") || it.contains("MB") || it.contains("KB") || it.contains("GB") || it.contains(":")
                } ?: false

                var infoIcon = v.findViewWithTag<ImageView>("pref_info_button")
                if (hasDescription && pref !is PreferenceCategory) {
                    if (!isDynamicPathOrValue) {
                        summaryView?.visibility = View.GONE
                    } else {
                        summaryView?.visibility = View.VISIBLE
                    }

                    if (infoIcon == null) {
                        infoIcon = ImageView(v.context).apply {
                            tag = "pref_info_button"
                            val size = (36 * resources.displayMetrics.density).toInt()
                            val p = (8 * resources.displayMetrics.density).toInt()
                            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                gravity = Gravity.CENTER_VERTICAL
                                marginStart = (4 * resources.displayMetrics.density).toInt()
                                marginEnd = (4 * resources.displayMetrics.density).toInt()
                            }
                            setPadding(p, p, p, p)
                            setImageResource(R.drawable.ic_info)
                            val outValue = TypedValue()
                            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                            setBackgroundResource(outValue.resourceId)

                            val colorAttr = TypedValue()
                            context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, colorAttr, true)
                            imageTintList = ColorStateList.valueOf(colorAttr.data)
                        }
                        if (seekBar != null) {
                            val titleParent = titleView?.parent as? ViewGroup
                            if (titleParent != null && infoIcon.parent == null) {
                                infoIcon.id = View.generateViewId()
                                val density = resources.displayMetrics.density
                                val iconSize = (32 * density).toInt()
                                val p = (6 * density).toInt()
                                infoIcon.setPadding(p, p, p, p)
                                if (titleParent is android.widget.RelativeLayout) {
                                    val seekbarVal = titleParent.findViewById<View>(androidx.preference.R.id.seekbar_value)
                                    val lp = android.widget.RelativeLayout.LayoutParams(iconSize, iconSize).apply {
                                        if (seekbarVal != null) {
                                            addRule(android.widget.RelativeLayout.START_OF, androidx.preference.R.id.seekbar_value)
                                            addRule(android.widget.RelativeLayout.CENTER_VERTICAL, androidx.preference.R.id.seekbar_value)
                                        } else {
                                            addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                                            addRule(android.widget.RelativeLayout.ALIGN_TOP, android.R.id.title)
                                        }
                                        marginEnd = (6 * density).toInt()
                                        marginStart = (6 * density).toInt()
                                    }
                                    infoIcon.layoutParams = lp
                                    (titleView.layoutParams as? android.widget.RelativeLayout.LayoutParams)?.let { tLp ->
                                        tLp.addRule(android.widget.RelativeLayout.START_OF, infoIcon.id)
                                    }
                                    titleParent.addView(infoIcon)
                                } else if (titleParent is LinearLayout) {
                                    val lp = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                                        gravity = Gravity.CENTER_VERTICAL
                                        marginStart = (6 * density).toInt()
                                        marginEnd = (6 * density).toInt()
                                    }
                                    infoIcon.layoutParams = lp
                                    (titleView.layoutParams as? LinearLayout.LayoutParams)?.weight = 1f
                                    val index = titleParent.indexOfChild(titleView)
                                    titleParent.addView(infoIcon, (index + 1).coerceAtMost(titleParent.childCount))
                                } else {
                                    titleParent.addView(infoIcon)
                                }
                            }
                        } else {
                            val widgetFrame = v.findViewById<ViewGroup>(android.R.id.widget_frame)
                            if (widgetFrame != null && v is ViewGroup) {
                                val index = v.indexOfChild(widgetFrame)
                                if (index >= 0) {
                                    v.addView(infoIcon, index)
                                } else {
                                    v.addView(infoIcon)
                                }
                            } else if (v is ViewGroup) {
                                v.addView(infoIcon)
                            }
                        }
                    }

                    infoIcon.visibility = View.VISIBLE
                    infoIcon.setOnClickListener {
                        MaterialAlertDialogBuilder(v.context)
                            .setTitle(pref?.title ?: v.context.getString(R.string.info_tooltip_title))
                            .setMessage(summaryText)
                            .setPositiveButton(R.string.info_got_it, null)
                            .show()
                    }
                } else {
                    infoIcon?.visibility = View.GONE
                }
            }

            override fun onChildViewDetachedFromWindow(v: View) {}
        })

        val keyToHighlight = arguments?.getString("highlight_key")
        if (keyToHighlight != null) {
            val adapter = listView.adapter as? PreferenceGroupAdapter
            val position = adapter?.getPreferenceAdapterPosition(keyToHighlight) ?: -1

            if (position != -1) {
                listView.postDelayed({
                    listView.smoothScrollToPosition(position + 1)

                    listView.postDelayed({
                        val holder = listView.findViewHolderForAdapterPosition(position)
                        holder?.itemView?.let { itemView ->
                            val originalColor = itemView.background
                            itemView.setBackgroundColor(requireContext().getColor(android.R.color.system_control_highlight_light))
                            itemView.postDelayed({
                                itemView.background = originalColor
                            }, 1000)
                        }
                    }, 300)
                }, 200)
            }
        }
    }
}