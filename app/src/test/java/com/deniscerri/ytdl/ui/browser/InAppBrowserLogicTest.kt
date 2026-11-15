package com.deniscerri.ytdl.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBrowserLogicTest {

    @Test
    fun adBlockerMatchesExactAdHostsAndSubdomainsOnly() {
        assertTrue(AdBlocker.isAdHost("doubleclick.net"))
        assertTrue(AdBlocker.isAdHost("pagead2.googlesyndication.com"))
        assertTrue(AdBlocker.isAdHost("SUB.POPADS.NET."))

        assertFalse(AdBlocker.isAdHost("notdoubleclick.net"))
        assertFalse(AdBlocker.isAdHost("akwam.to"))
        assertFalse(AdBlocker.isAdHost("www.google.com"))
        assertFalse(AdBlocker.isAdHost("youtube.com"))
        assertFalse(AdBlocker.isAdHost(""))
        assertFalse(AdBlocker.isAdHost(null))
    }

    @Test
    fun classifiesDirectDownloadAndPassiveMediaUrls() {
        assertTrue(BrowserDownloadInterceptor.isDirectDownloadUrl("https://cdn.akwam.to/dl/Movie.1080p.mp4?token=abc"))
        assertTrue(BrowserDownloadInterceptor.isDirectDownloadUrl("https://dl.example.com/archive.RAR#part1"))
        assertTrue(BrowserDownloadInterceptor.isDirectDownloadUrl("https://dl.example.com/app.apk"))
        assertFalse(BrowserDownloadInterceptor.isDirectDownloadUrl("https://akwam.to/movie/1234"))
        assertFalse(BrowserDownloadInterceptor.isDirectDownloadUrl("blob:https://example.com/uuid-video.mp4"))

        assertTrue(BrowserDownloadInterceptor.isArchiveOrBinaryUrl("https://dl.example.com/package.zip"))
        assertFalse(BrowserDownloadInterceptor.isArchiveOrBinaryUrl("https://cdn.example.com/video.mp4"))

        assertTrue(BrowserDownloadInterceptor.isPassiveStreamUrl("https://stream.example.com/master.m3u8?e=123"))
        assertTrue(BrowserDownloadInterceptor.isPassiveStreamUrl("https://stream.example.com/manifest.mpd"))
        assertTrue(BrowserDownloadInterceptor.isPassiveStreamUrl("https://stream.example.com/video.mkv"))
        assertFalse(BrowserDownloadInterceptor.isPassiveStreamUrl("https://example.com/script.js"))
    }

    @Test
    fun streamRegistryDeduplicatesAndPreservesLatestOrder() {
        val registry = BrowserDownloadInterceptor.StreamRegistry()
        assertTrue(
            registry.record(
                url = "https://cdn.example.com/720p.mp4",
                referer = "https://site.example/watch/1",
                pageTitle = "Episode 1",
            )
        )
        assertFalse(
            registry.record(
                url = "https://cdn.example.com/720p.mp4",
                referer = "https://site.example/watch/1",
                pageTitle = "Episode 1",
            )
        )
        assertTrue(
            registry.record(
                url = "https://cdn.example.com/master.m3u8",
                referer = "https://site.example/watch/1",
                pageTitle = "Episode 1",
            )
        )
        // Ad stream host must not be recorded even if URL ends in .mp4
        assertFalse(
            registry.record(
                url = "https://ads.doubleclick.net/promo.mp4",
                referer = "https://site.example/watch/1",
                pageTitle = "Ad",
            )
        )

        val snapshot = registry.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals("https://cdn.example.com/master.m3u8", snapshot.first().url)
        assertEquals("m3u8", snapshot.first().extension)

        registry.clear()
        assertEquals(0, registry.count())
    }

    @Test
    fun ytDlpHeaderCommandsSanitizeControlCharsAndEscapeQuotes() {
        val cmd = BrowserDownloadInterceptor.buildYtDlpHeaderCommands(
            cookie = "cf_clearance=abc123\r\nEvil: injected",
            userAgent = "Mozilla/5.0 \"Custom\"",
            referer = "https://site.example/watch/1",
        )
        assertFalse(cmd.contains('\r'))
        assertFalse(cmd.contains('\n'))
        assertTrue(cmd.contains("--add-header \"Cookie:cf_clearance=abc123Evil: injected\""))
        assertTrue(cmd.contains("--add-header \"Referer:https://site.example/watch/1\""))
        assertTrue(cmd.contains("--add-header \"User-Agent:Mozilla/5.0 \\\"Custom\\\"\""))
    }
}
