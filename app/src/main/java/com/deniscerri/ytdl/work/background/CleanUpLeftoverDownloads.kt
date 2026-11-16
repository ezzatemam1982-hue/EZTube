package com.deniscerri.ytdl.work.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

class CleanUpLeftoverDownloads(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val notificationUtil = NotificationUtil(App.Companion.instance)
        val id = System.currentTimeMillis().toInt()

        val notification = notificationUtil.createDeletingLeftoverDownloadsNotification()
        if (Build.VERSION.SDK_INT >= 33) {
            setForegroundAsync(
                ForegroundInfo(
                    id,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            )
        }else{
            setForegroundAsync(ForegroundInfo(id, notification))
        }

        val dbManager = DBManager.Companion.getInstance(context)
        val downloadRepo = DownloadRepository(dbManager.downloadDao)
        downloadRepo.deleteCancelled()
        downloadRepo.deleteErrored()

        val activeDownloadCount = downloadRepo.getActiveDownloadsCount()
        if (activeDownloadCount == 0){
            File(FileUtil.getCacheDownloadsPath(context)).deleteRecursively()
        }

        val interval = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("cleanup_leftover_downloads", DEFAULT_INTERVAL)
            .orEmpty()
        schedule(context, interval, ExistingWorkPolicy.REPLACE)

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "cleanup_leftover_downloads"
        const val WORK_TAG = "cleanup_leftover_downloads"
        const val DEFAULT_INTERVAL = "weekly"

        fun schedule(
            context: Context,
            interval: String,
            existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
        ) {
            val workManager = WorkManager.getInstance(context)
            val nextTime = Calendar.getInstance()
            when (interval) {
                "daily" -> nextTime.add(Calendar.DAY_OF_WEEK, 1)
                "weekly" -> nextTime.add(Calendar.DAY_OF_WEEK, 7)
                "monthly" -> nextTime.add(Calendar.MONTH, 1)
                else -> {
                    workManager.cancelUniqueWork(WORK_NAME)
                    workManager.cancelAllWorkByTag(WORK_TAG)
                    return
                }
            }

            val workConstraints = Constraints.Builder()
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val allowMeteredNetworks = preferences.getBoolean("metered_networks", true)
            if (!allowMeteredNetworks) {
                workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)
            }

            val delay = (nextTime.timeInMillis - System.currentTimeMillis())
                .coerceAtLeast(TimeUnit.MINUTES.toMillis(15))
            val workRequest = OneTimeWorkRequestBuilder<CleanUpLeftoverDownloads>()
                .addTag(WORK_TAG)
                .setConstraints(workConstraints.build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            workManager.enqueueUniqueWork(WORK_NAME, existingWorkPolicy, workRequest)
        }
    }

}