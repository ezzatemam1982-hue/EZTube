package com.clipflow.app.player

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Safe initializer for Firebase Analytics in EZTube.
 * Tracks active users (Real-time and Daily Active Users) with zero friction and zero personal data collection.
 * Designed with safe fallbacks: if google-services.json is missing or Firebase fails to initialize,
 * the app continues to operate normally with a helpful log warning.
 */
object AnalyticsTracker {
    private const val TAG = "EZTubeAnalytics"
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        try {
            val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            }

            if (app != null) {
                FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(true)
                initialized = true
                Log.i(TAG, "Firebase Analytics initialized successfully for package ${context.packageName}.")
            } else {
                Log.w(
                    TAG,
                    "FirebaseApp could not be initialized. If running in production, ensure app/google-services.json is present."
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase Analytics init skipped or deferred: ${t.message}")
        }
    }
}
