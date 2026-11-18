package com.deniscerri.ytdl.ui.more.settings.downloading

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.AlarmScheduler
import com.deniscerri.ytdl.work.background.CleanUpLeftoverDownloads
import com.deniscerri.ytdl.work.DownloadWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DownloadSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        when(pref.key) {
            "update_download_system" -> {
                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    val updateUtil = com.deniscerri.ytdl.util.UpdateUtil(context)
                    host.hostLifecycleOwner.lifecycleScope.launch {
                        val view = host.hostView
                        pref.isEnabled = false
                        pref.summary = context.getString(R.string.update_download_system_checking)
                        runCatching {
                            val res = updateUtil.updateYTDL()
                            when (res.status) {
                                com.deniscerri.ytdl.util.UpdateUtil.YTDLPUpdateStatus.DONE -> {
                                    if (view != null && view.isAttachedToWindow) {
                                        com.google.android.material.snackbar.Snackbar.make(
                                            view,
                                            context.getString(R.string.update_download_system_success),
                                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                        ).show()
                                    }
                                    withContext(Dispatchers.IO) {
                                        java.io.File(FileUtil.getInfoJsonPath(context)).deleteRecursively()
                                    }
                                }
                                com.deniscerri.ytdl.util.UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE -> {
                                    if (view != null && view.isAttachedToWindow) {
                                        com.google.android.material.snackbar.Snackbar.make(
                                            view,
                                            context.getString(R.string.app_update_latest_installed),
                                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                                com.deniscerri.ytdl.util.UpdateUtil.YTDLPUpdateStatus.ERROR -> {
                                    if (view != null && view.isAttachedToWindow) {
                                        com.google.android.material.snackbar.Snackbar.make(
                                            view,
                                            context.getString(R.string.player_error_offline),
                                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                                else -> Unit
                            }
                        }.onFailure {
                            if (view != null && view.isAttachedToWindow) {
                                com.google.android.material.snackbar.Snackbar.make(
                                    view,
                                    context.getString(R.string.player_error_offline),
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                                ).show()
                            }
                        }
                        pref.isEnabled = true
                        pref.summary = context.getString(R.string.update_download_system_summary)
                    }
                    true
                }
            }
            "remember_download_type" -> {
                val rememberDownloadType = pref as SwitchPreferenceCompat
                rememberDownloadType.setOnPreferenceChangeListener { _, newValue ->
                    host.findPref("preferred_download_type")?.isEnabled = !(newValue as Boolean)
                    host.refreshUI()
                    true
                }
            }
            "prevent_duplicate_downloads" -> {
                val archivePath = host.findPref("download_archive_path")
                archivePath?.isVisible = false
                pref.setOnPreferenceChangeListener { _, _ ->
                    archivePath?.isVisible = false
                    host.refreshUI()
                    true
                }
            }
            "download_delay" -> {
                fun setSummary(firstVal: Float, secondVal: Float) {
                    if (firstVal == 0f && secondVal == 0f) {
                        pref.summary = context.getString(R.string.no_delay)
                    } else {
                        pref.summary = "${firstVal}s - ${secondVal}s"
                    }
                }
                val prefValue = preferences.getString("download_delay", "0-0")!!.split("-").map { it.toFloat() }
                setSummary(prefValue.first(), prefValue.last())

                pref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        UiUtil.showDownloadDelayDialog(
                            host.getHostContext(),
                            preferences,
                            rangeSelected = { res ->
                                preferences.edit(commit = true) {
                                    putString("download_delay", "${res.first}-${res.second}")
                                    setSummary(res.first, res.second)
                                    host.refreshUI()
                                }
                            },
                            resetSelected = {
                                preferences.edit(commit = true) {
                                    putString("download_delay", "0-0")
                                    setSummary(0f, 0f)
                                    host.refreshUI()
                                }
                            }
                        )
                        true
                    }
            }
            "download_archive_path" -> {
                pref.summary = FileUtil.getDownloadArchivePath(context)
                pref.isVisible = false
                pref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        host.activityResultDelegate.launch(intent) { result ->
                            result.data?.data?.let {
                                host.getHostContext().contentResolver?.takePersistableUriPermission(
                                    it,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )
                            }

                            val path = result.data!!.data.toString()
                            preferences.edit(commit = true) {
                                putString("download_archive_path", path)
                            }
                            pref.summary = FileUtil.getDownloadArchivePath(context)
                            host.refreshUI()
                        }
                        true
                    }
            }
            "cleanup_leftover_downloads" -> {
                pref.setOnPreferenceChangeListener { _, newValue ->
                    CleanUpLeftoverDownloads.schedule(context, (newValue as? String).orEmpty(), ExistingWorkPolicy.REPLACE)
                    host.refreshUI()
                    true
                }
            }
            "use_alarm_for_scheduling" -> {
                val scheduler = AlarmScheduler(context)
                pref.setOnPreferenceChangeListener { preference, newValue ->
                    var allowChange = true
                    if (newValue as Boolean){
                        if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31){
                            Intent().also { intent ->
                                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                intent.data = Uri.parse("package:${context.packageName}")
                                host.getHostContext().startActivity(intent)
                            }
                            allowChange = false
                        }
                    }
                    host.refreshUI()
                    allowChange
                }
            }
            "use_scheduler" -> {
                val scheduler = AlarmScheduler(context)

                val useScheduler = pref as SwitchPreferenceCompat
                useScheduler.setOnPreferenceChangeListener { preference, newValue ->
                    var allowChange = true
                    if (newValue as Boolean){
                        if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31){
                            Intent().also { intent ->
                                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                host.getHostContext().startActivity(intent)
                            }
                            allowChange = false
                        }else{
                            scheduler.schedule()
                        }
                    }else{
                        scheduler.cancel()
                        //start worker if there are leftover downloads waiting for scheduler
                        val workConstraints = Constraints.Builder()
                        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                            .addTag("download")
                            .setConstraints(workConstraints.build())
                            .setInitialDelay(1000L, TimeUnit.MILLISECONDS)

                        WorkManager.getInstance(context).enqueueUniqueWork(
                            System.currentTimeMillis().toString(),
                            ExistingWorkPolicy.REPLACE,
                            workRequest.build()
                        )
                    }
                    host.refreshUI()
                    host.findPref("schedule_start")?.isEnabled = allowChange && newValue as Boolean
                    host.findPref("schedule_end")?.isEnabled = allowChange && newValue as Boolean
                    allowChange
                }
            }
            "schedule_start" -> {
                val scheduler = AlarmScheduler(context)
                pref.isEnabled = preferences.getBoolean("use_scheduler", false)
                pref.summary = preferences.getString("schedule_start", "00:00")
                pref.setOnPreferenceClickListener {
                    UiUtil.showTimePicker(host.requestGetParentFragmentManager(), preferences){
                        val hr = it.get(Calendar.HOUR_OF_DAY)
                        val mn = it.get(Calendar.MINUTE)
                        val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                        preferences.edit(commit = true) {
                            putString("schedule_start", formattedTime)
                        }
                        pref.summary = formattedTime
                        scheduler.schedule()
                    }
                    host.refreshUI()
                    true
                }
            }
            "schedule_end" -> {
                val scheduler = AlarmScheduler(context)
                pref.isEnabled = preferences.getBoolean("use_scheduler", false)
                pref.summary = preferences.getString("schedule_end", "05:00")
                pref.setOnPreferenceClickListener {
                    UiUtil.showTimePicker(host.requestGetParentFragmentManager(), preferences){
                        val hr = it.get(Calendar.HOUR_OF_DAY)
                        val mn = it.get(Calendar.MINUTE)
                        val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                        preferences.edit(commit = true) {
                            putString("schedule_end",formattedTime)
                        }
                        pref.summary = formattedTime
                        scheduler.schedule()
                    }
                    host.refreshUI()
                    true
                }
            }
            "proxy" -> {
                (pref as EditTextPreference).apply {
                    val s = context.getString(R.string.socks5_proxy_summary)
                    summary = if (text.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${text}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${newValue}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "preferred_download_type" -> {
                (pref as ListPreference).apply {
                    val s = context.getString(R.string.preferred_download_type_summary)
                    summary = if (value.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${entries[entryValues.indexOf(value)]}]"
                    }
                    isEnabled = !preferences.getBoolean("remember_download_type", false)
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${entries[entryValues.indexOf(newValue)]}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "buffer_size" -> {
                (pref as EditTextPreference).apply {
                    val s = context.getString(R.string.buffer_size_summary)
                    summary = if (text.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${text}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${newValue}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "socket_timeout" -> {
                (pref as EditTextPreference).apply {
                    val s = context.getString(R.string.socket_timeout_description)
                    summary = if (text.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${text}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${newValue}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
        }

    }
}