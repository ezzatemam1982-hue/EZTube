package com.deniscerri.ytdl.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spanned
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.google.android.material.color.DynamicColors


object ThemeUtil {

    private val activities = mutableListOf<Activity>()

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                activities.add(p0)
            }

            override fun onActivityStarted(p0: Activity) {

            }

            override fun onActivityResumed(p0: Activity) {

            }

            override fun onActivityPaused(p0: Activity) {

            }

            override fun onActivityStopped(p0: Activity) {

            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {

            }

            override fun onActivityDestroyed(p0: Activity) {
                activities.remove(p0)
            }
        })

    }

    sealed class AppIcon(
        @StringRes val nameResource: Int,
        @DrawableRes val iconResource: Int,
        val activityAlias: String
    ) {
        object Default : AppIcon(R.string.auto, R.mipmap.ic_launcher, "Default")
        object Light : AppIcon(R.string.light, R.mipmap.ic_launcher_light, "LightIcon")
        object Dark : AppIcon(R.string.dark, R.mipmap.ic_launcher_dark, "DarkIcon")
        object Blue : AppIcon(R.string.blue, R.mipmap.ic_launcher_blue, "BlueIcon")
        object Green : AppIcon(R.string.green, R.mipmap.ic_launcher_green, "GreenIcon")
    }

    val availableIcons = listOf(
        AppIcon.Default,
        AppIcon.Light,
        AppIcon.Dark,
        AppIcon.Blue,
        AppIcon.Green,
    )

    fun recreateMain() {
        activities.firstOrNull { it.javaClass == MainActivity::class.java }?.recreate()
    }

    fun recreateAllActivities() {
        activities.forEach {
            it.recreate()
        }
    }

    fun currentThemeSignature(): String =
        "${com.clipflow.app.player.ThemeSettings.seed ?: "wallpaper"}|${com.clipflow.app.player.ThemeSettings.mode}|${com.clipflow.app.player.ThemeSettings.amoled}"

    fun legacyIconThemeMode(): String = when (com.clipflow.app.player.ThemeSettings.mode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }

    fun onUnifiedThemeChanged() {
        applyNightMode(com.clipflow.app.player.ThemeSettings.mode)
        activities.filterNot { it.javaClass == MainActivity::class.java }.forEach {
            it.recreate()
        }
    }

    private fun applyNightMode(mode: String) {
        when (mode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun updateThemes() {
        applyNightMode(com.clipflow.app.player.ThemeSettings.mode)
        activities.forEach {
            updateTheme(it)
            it.recreate()
        }
    }

    fun updateTheme(activity: Activity) {
        com.clipflow.app.player.ThemeSettings.ensureInit(activity.applicationContext)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val mode = com.clipflow.app.player.ThemeSettings.mode
        applyNightMode(mode)

        val seed = com.clipflow.app.player.ThemeSettings.seed
        when {
            seed == null -> {
                activity.setTheme(R.style.BaseTheme)
                DynamicColors.applyToActivityIfAvailable(activity)
            }
            seed.equals(com.clipflow.app.player.SEED_YOUTUBE, ignoreCase = true) -> {
                activity.setTheme(R.style.Theme_YouTube)
            }
            seed.equals(com.clipflow.app.player.SEED_CREAM_ROSE, ignoreCase = true) -> {
                activity.setTheme(R.style.Theme_CreamRose)
            }
            seed.equals(com.clipflow.app.player.SEED_AURORA, ignoreCase = true) -> {
                activity.setTheme(R.style.Theme_Erica)
            }
            else -> {
                activity.setTheme(R.style.Theme_YouTube)
            }
        }

        if (com.clipflow.app.player.ThemeSettings.amoled && mode != "light") {
            activity.theme.applyStyle(R.style.Pure, true)
        }

        val iconMode = sharedPreferences.getString("ytdlnis_icon", "Default") ?: "Default"
        updateAppIcon(activity, legacyIconThemeMode(), iconMode)
    }

    fun getThemeColor(context: Context, colorCode: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(colorCode, value, true)
        return value.data
    }

    /**
     * Get the styled app name
     */
    fun getStyledAppName(context: Context): Spanned {
        val colorPrimary = getThemeColor(context, androidx.appcompat.R.attr.colorPrimaryDark)
        val hexColor = "#%06X".format(0xFFFFFF and colorPrimary)
        return "<span  style='color:$hexColor';>EZ</span>Tube"
            .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)
    }


    fun updateAppIcon(activity: Activity, theme: String, appIconMode: String) {
        //disable old icons
        for (appIcon in availableIcons) {
            val activityClass = "com.deniscerri.ytdl." + appIcon.activityAlias

            // remove old icons
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.packageName, activityClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        var iconMode = appIconMode
        if (appIconMode == "Default") {
            iconMode = theme
        }

        when (iconMode) {
            "LightIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.LightIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "DarkIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.DarkIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "BlueIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.BlueIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "GreenIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.GreenIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            // or "System"
            else -> {
                //set dynamic icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.Default"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}