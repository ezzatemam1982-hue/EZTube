package com.clipflow.app.player

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import kotlinx.coroutines.MainScope
import org.schabi.newpipe.extractor.NewPipe

/**
 * Initializes all ClipFlow streaming player subsystems and shared resources inside [com.deniscerri.ytdl.App].
 */
object PlayerInitializer {

    /** The Shorts feed, players and bank: one per process, outliving any window. */
    lateinit var shorts: ShortsState
        private set

    fun init(app: Application) {
        // Before anything that could crash, so its stack trace is kept (Settings › About › Crash report).
        CrashLog.install(app)
        // First: everything below may already word something (str()).
        AppLanguage.init(app)
        NewPipe.init(NewPipeDownloader.instance)
        // The iOS client only adds HLS livestream formats; ClipFlow plays VOD, so skip that request.
        org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(false)
        ThemeSettings.init(app)
        QualityPreference.init(app)
        PlaybackSpeed.init(app)
        AudioOnly.init(app)
        PlayerControls.init(app)
        FullscreenZoom.init(app)
        FullscreenSwipes.init(app)
        SeekStep.init(app)
        ChaptersStayOpen.init(app)
        AutoPip.init(app)
        LiveControls.init(app)
        CaptionSettings.init(app)
        CaptionTranslator.refresh()
        SearchHistory.init(app)
        ResumeStore.init(app)
        SavedSession.init(app)
        AuthSessionManager.init(app)
        Account.init(app)
        AuthSessionManager.restoreCookiesToCookieManager()
        Bells.init(app)
        NewVideos.schedule(app)
        SponsorBlock.init(app)
        ShortsSetting.init(app)
        ShortsBank.init(app)
        AppUpdate.init(app)
        shorts = ShortsState(app, MainScope())
        // Stream URLs are bound to the network they were fetched on: forget them on a switch.
        app.getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var last: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    if (last != null && last != network) StreamCache.invalidateAll()
                    last = network
                }
            },
        )
    }

    // Thumbnails are in every list and the player; a short crossfade replaces pop-in.
    fun createImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(Net.client)
        .crossfade(180)
        .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.25).build() }
        .build()
}
