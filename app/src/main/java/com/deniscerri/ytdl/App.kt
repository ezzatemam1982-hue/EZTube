package com.deniscerri.ytdl

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.ExecuteException
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.services.BgUtilsPoTokenGeneratorService
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.AppPreferenceDefaults
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import com.deniscerri.ytdl.util.Extensions.hasReachedEnd
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.ObserveAlarmScheduler
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.work.background.CleanUpLeftoverDownloads
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.clipflow.app.player.PlayerInitializer


class App : Application(), ImageLoaderFactory {

    private val firstUiRendered = CompletableDeferred<Unit>()

    internal fun notifyFirstUiRendered() {
        firstUiRendered.complete(Unit)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@App)
        setDefaultValues()
        ThemeUtil.init(this)

        val processName = getProcessNameImpl()
        if (processName.endsWith(":incognito_process") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("incognito_store")
            return
        }

        PlayerInitializer.init(this)

        applicationScope = CoroutineScope(SupervisorJob())
        applicationScope.launch((Dispatchers.IO)) {
            try {
                createNotificationChannels()
                initLibraries()

                launch {
                    firstUiRendered.await()
                    com.deniscerri.ytdl.util.UpdateUtil(this@App).silentlyUpdateYtdlpIfDue()
                }

                val appVer = sharedPreferences.getString("version", "")!!
                if(appVer.isEmpty() || appVer != BuildConfig.VERSION_NAME){
                    sharedPreferences.edit {
                        putString("version", BuildConfig.VERSION_NAME)
                    }
                }

                val cleanupInterval = sharedPreferences.getString(
                    "cleanup_leftover_downloads",
                    CleanUpLeftoverDownloads.DEFAULT_INTERVAL
                ).orEmpty()
                CleanUpLeftoverDownloads.schedule(this@App, cleanupInterval, ExistingWorkPolicy.KEEP)

                val db = DBManager.getInstance(this@App)
                val scheduler = ObserveAlarmScheduler(this@App)
                db.observeSourcesDao.getAllSources()
                    .filter { it.status == ObserveSourcesRepository.SourceStatus.ACTIVE && !it.hasReachedEnd() }
                    .forEach { scheduler.schedule(it) }         // idempotent: FLAG_UPDATE_CURRENT updates in place

            }catch (e: Exception){
                Looper.prepare().runCatching {
                    Toast.makeText(this@App, e.message, Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }
    override fun newImageLoader(): ImageLoader = PlayerInitializer.createImageLoader(this)

    @Throws(ExecuteException::class)
    private fun initLibraries() {
        RuntimeManager.getInstance().init(this)
    }

    private fun setDefaultValues(){
        val SPL = 1
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (sp.getInt("spl", 0) != SPL) {
            PreferenceManager.setDefaultValues(this, R.xml.root_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.downloading_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.general_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.processing_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.folders_preference, true)
            PreferenceManager.setDefaultValues(this, R.xml.updating_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.ytdlp_update_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.app_update_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.advanced_preferences, true)
            sp.edit(commit = true) {
                putString("ytdlp_source", "stable")
                putString("format_order", "filesize")
                putString("format_filter", "ALL")
                putBoolean("update_formats", true)
                putString("apk_install_method", "system")
                putBoolean("force_keyframes", false)
                putBoolean("recode_video", false)
                putBoolean("compatible_video", false)
                putBoolean("save_lrc", false)
                putBoolean("all_artists_in_album_artist", false)
                putBoolean("prefer_drc_audio", false)
                putString("sponsorblock_url", "")
                putBoolean("remove_audio", false)
                putBoolean("also_download_audio", false)
                putString("format_id", "")
                putString("format_id_audio", "")
                putString("preferred_format_size", "")
                putString("preferred_format_size_audio", "")
                putString("subs_lang", "ar, en, .*-orig")
                putInt("spl", SPL)
            }
        }
        AppPreferenceDefaults.migrateAndApply(this)
    }

    private fun createNotificationChannels() {
        val notificationUtil = NotificationUtil(this)
        notificationUtil.createNotificationChannel()
    }

    private fun getProcessNameImpl() : String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            packageName
        }
    }

    companion object {
        private const val TAG = "App"
        private lateinit var applicationScope: CoroutineScope
        lateinit var instance: App
    }
}