package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.LayoutDirection
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.core.text.layoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.RestoreAppDataItem
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.util.LocaleUtil
import com.deniscerri.ytdl.util.BackupSettingsUtil
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainSettingsFragment : PreferenceFragmentCompat() {
    private var backup : Preference? = null
    private var restore : Preference? = null
    private var backupPath : Preference? = null

    private var activeDownloadCount = 0

    private lateinit var settingsViewModel: SettingsViewModel

    private lateinit var editor: SharedPreferences.Editor


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.clipToPadding = false
        val initialBottomPadding = listView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val navBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()
            )

            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                initialBottomPadding + navBars.bottom
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
                    if (pref != null && pref !is PreferenceCategory) {
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

                if (pref != null && pref !is PreferenceCategory) {
                    v.setBackgroundResource(R.drawable.bg_settings_card)
                }

                val titleView = v.findViewById<TextView>(android.R.id.title)
                if (titleView != null) {
                    titleView.maxLines = 1
                    titleView.ellipsize = TextUtils.TruncateAt.END
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        titleView,
                        12,
                        16,
                        1,
                        TypedValue.COMPLEX_UNIT_SP
                    )
                }

                val summaryView = v.findViewById<TextView>(android.R.id.summary)
                if (summaryView != null) {
                    summaryView.visibility = View.VISIBLE
                    summaryView.maxLines = 2
                    summaryView.ellipsize = TextUtils.TruncateAt.END
                }
            }

            override fun onChildViewDetachedFromWindow(v: View) {}
        })
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        val navController = findNavController()
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        editor = preferences.edit()

        val appearance = findPreference<Preference>("appearance")
        val separator = if (LocaleUtil.currentLocale().layoutDirection == LayoutDirection.RTL) "،" else ","
        appearance?.summary = "${getString(R.string.app_icon)}$separator ${getString(R.string.hide_thumbnails)}$separator ${getString(R.string.preferred_search_engine)}"
        appearance?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_appearanceSettingsFragment)
            true
        }

        val folders = findPreference<Preference>("folders")
        folders?.summary = "${getString(R.string.music_directory)}$separator ${getString(R.string.video_directory)}$separator ${getString(R.string.temporary_files)}"
        folders?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_folderSettingsFragment)
            true
        }

        val downloading = findPreference<Preference>("downloading")
        downloading?.summary = "${getString(R.string.concurrent_fragments)}$separator ${getString(R.string.concurrent_downloads)}"
        downloading?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_downloadSettingsFragment)
            true
        }

        val processing = findPreference<Preference>("processing")
        processing?.summary = "${getString(R.string.sponsorblock)}$separator ${getString(R.string.embed_subtitles)}$separator ${getString(R.string.add_chapters)}"
        processing?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_processingSettingsFragment)
            true
        }

        val advanced = findPreference<Preference>("advanced")
        advanced?.summary = getString(R.string.use_app_language_for_metadata)
        advanced?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_advancedSettingsFragment)
            true
        }

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        backup = findPreference("backup")
        restore = findPreference("restore")
        backupPath = findPreference("backup_path")

        backup!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle(getString(R.string.select_backup_categories))
                val values = resources.getStringArray(R.array.backup_category_values)
                val entries = resources.getStringArray(R.array.backup_category_entries)
                val checkedItems : ArrayList<Boolean> = arrayListOf()
                values.forEach { _ ->
                    checkedItems.add(true)
                }

                builder.setMultiChoiceItems(
                    entries,
                    checkedItems.toBooleanArray()
                ) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }

                builder.setPositiveButton(
                    getString(R.string.ok)
                ) { _: DialogInterface?, _: Int ->
                    lifecycleScope.launch {
                        if (checkedItems.all { !it }){
                            Snackbar.make(requireView(), R.string.select_backup_categories, Snackbar.LENGTH_SHORT).show()
                            return@launch
                        }

                        val selectedItems = values.mapIndexed { idx, it -> Pair<String, Boolean>(it, checkedItems[idx]) }.filter { it.second }.map { it.first }
                        val pathResult = withContext(Dispatchers.IO){
                            settingsViewModel.backup(selectedItems)
                        }

                        if (pathResult.isFailure) {
                            val errorMessage = pathResult.exceptionOrNull()?.message ?: requireContext().getString(R.string.errored)
                            val snack = Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG)
                            val snackbarView: View = snack.view
                            val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                            snackTextView.maxLines = 9999999
                            snack.setAction(android.R.string.copy){
                                UiUtil.copyToClipboard(errorMessage ?: requireContext().getString(R.string.errored), requireActivity())
                            }
                            snack.show()
                        }else {
                            val s = Snackbar.make(
                                requireView(),
                                getString(R.string.backup_created_successfully),
                                Snackbar.LENGTH_LONG
                            )
                            s.setAction(R.string.open_file) {
                                FileUtil.openFileIntent(requireActivity(), pathResult.getOrNull()!!)
                            }
                            s.show()
                        }
                    }
                }

                // handle the negative button of the alert dialog
                builder.setNegativeButton(
                    getString(R.string.cancel)
                ) { _: DialogInterface?, _: Int -> }


                val dialog = builder.create()
                dialog.show()
                true
            }
        restore!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, BackupSettingsUtil.RESTORE_MIME_TYPES)
                }
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                appRestoreResultLauncher.launch(intent)
                true
            }

        backupPath?.apply {
            summary = formatLtrPathSummary(FileUtil.formatPath(FileUtil.getBackupPath(requireContext())))
            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                backupPathResultLauncher.launch(intent)
                true
            }
        }
    }

    private fun formatLtrPathSummary(path: String): String =
        BidiFormatter.getInstance(LocaleUtil.currentLocale()).unicodeWrap(path, TextDirectionHeuristicsCompat.LTR)

    private var backupPathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val path = result.data!!.data.toString()
            backupPath!!.summary = formatLtrPathSummary(FileUtil.formatPath(path))
            editor.putString("backup_path", path)
            editor.apply()
        }
    }

    private var appRestoreResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            lifecycleScope.launch {
                runCatching {
                    val (restoreData, summary) = BackupSettingsUtil.parse(requireContext(), result.data!!.data!!)
                    val parsedDataMessage = StringBuilder(summary)

                    showAppRestoreInfoDialog(
                        onMerge = {
                            lifecycleScope.launch {
                                val res = withContext(Dispatchers.IO){
                                    settingsViewModel.restoreData(restoreData, requireContext())
                                }
                                if (res) {
                                    showRestoreFinishedDialog(restoreData, parsedDataMessage.toString())
                                }else{
                                    throw Error()
                                }
                            }
                        },
                        onReset =  {
                            lifecycleScope.launch {
                                val res = withContext(Dispatchers.IO){
                                    settingsViewModel.restoreData(restoreData, requireContext(),true)
                                }
                                if (res) {
                                    showRestoreFinishedDialog(restoreData, parsedDataMessage.toString())
                                }else{
                                    throw Error()
                                }
                            }
                        }
                    )

                }.onFailure {
                    it.printStackTrace()
                    Snackbar.make(requireView(), it.message.toString(), Snackbar.LENGTH_INDEFINITE).show()
                }
            }

        }
    }



    @SuppressLint("RestrictedApi")
    private fun showAppRestoreInfoDialog(onMerge: () -> Unit, onReset: () -> Unit){
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage(getString(R.string.restore_info))

        builder.setNegativeButton(getString(R.string.cancel)) { dialog : DialogInterface?, _: Int ->
            dialog?.dismiss()
        }

        builder.setPositiveButton(getString(R.string.restore)) { dialog : DialogInterface?, _: Int ->
            onMerge()
            dialog?.dismiss()
        }

        builder.setNeutralButton(getString(R.string.reset)) { dialog : DialogInterface?, _: Int ->
            onReset()
            dialog?.dismiss()
        }


        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }

    private fun showRestoreFinishedDialog(data: RestoreAppDataItem, restoreDataMessage: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage("${getString(R.string.restore_complete)}\n\n$restoreDataMessage")
        builder.setCancelable(false)
        builder.setPositiveButton(
            getString(R.string.ok)
        ) { _: DialogInterface?, _: Int ->
            ThemeUtil.recreateAllActivities()
        }

        val dialog = builder.create()
        dialog.show()
    }
}