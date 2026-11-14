package com.clipflow.app.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class DefaultVideoQualitySelectionTest {

    @Before
    @After
    fun resetQualityPreference() {
        QualityPreference.clearVideoOverride()
        QualityPreference.setDefault(VideoQualityMode.Auto)
    }

    @Test
    fun settingsChoicesOrderMatchesSpecAndRoundTripsStorageKeys() {
        val expected = listOf(
            VideoQualityMode.Auto,
            VideoQualityMode.Highest,
            VideoQualityMode.Fixed(1080),
            VideoQualityMode.Fixed(720),
            VideoQualityMode.Fixed(480),
            VideoQualityMode.Fixed(360),
            VideoQualityMode.Fixed(240),
            VideoQualityMode.Fixed(144),
        )
        assertEquals(expected, VideoQualityMode.settingsChoices)

        for (mode in expected) {
            assertEquals(mode, VideoQualityMode.fromStorageKey(mode.storageKey))
        }
    }

    @Test
    fun fromStorageKeyFallsBackToAutoOnNullBlankOrInvalid() {
        assertEquals(VideoQualityMode.Auto, VideoQualityMode.fromStorageKey(null))
        assertEquals(VideoQualityMode.Auto, VideoQualityMode.fromStorageKey(""))
        assertEquals(VideoQualityMode.Auto, VideoQualityMode.fromStorageKey("   "))
        assertEquals(VideoQualityMode.Auto, VideoQualityMode.fromStorageKey("auto"))
        assertEquals(VideoQualityMode.Auto, VideoQualityMode.fromStorageKey("invalid"))
        assertEquals(VideoQualityMode.Auto, VideoQualityMode.fromStorageKey("0"))
        assertEquals(VideoQualityMode.Auto, VideoQualityMode.fromStorageKey("-144"))
        assertEquals(VideoQualityMode.Highest, VideoQualityMode.fromStorageKey("HIGHEST"))
        assertEquals(VideoQualityMode.Fixed(144), VideoQualityMode.fromStorageKey("144"))
        assertEquals(VideoQualityMode.Fixed(240), VideoQualityMode.fromStorageKey(" 240 "))
    }

    @Test
    fun resolveTargetHeightUsesExactClosestBelowOrLowestAvailableFallback() {
        assertEquals(144, StreamSelection.resolveTargetHeight(listOf(1080, 720, 480, 360, 240, 144), 144))
        assertEquals(240, StreamSelection.resolveTargetHeight(listOf(1080, 720, 480, 360, 240, 144), 240))
        assertEquals(480, StreamSelection.resolveTargetHeight(listOf(1080, 720, 480, 360), 480))
        assertEquals(720, StreamSelection.resolveTargetHeight(listOf(1080, 720, 480, 360), 720))
        assertEquals(1080, StreamSelection.resolveTargetHeight(listOf(2160, 1440, 1080, 720), 1080))

        // Closest lower when target resolution is unavailable
        assertEquals(360, StreamSelection.resolveTargetHeight(listOf(720, 360, 144), 480))
        assertEquals(144, StreamSelection.resolveTargetHeight(listOf(720, 360, 144), 240))

        // Lowest offered resolution when target is below everything available
        assertEquals(360, StreamSelection.resolveTargetHeight(listOf(720, 480, 360), 144))
        assertEquals(360, StreamSelection.resolveTargetHeight(listOf(720, 480, 360), 240))

        assertNull(StreamSelection.resolveTargetHeight(emptyList(), 720))
    }

    @Test
    fun selectVideoStreamHonorsAutoCeilingAndHighestAbove1080p() {
        val candidates = listOf(
            fakeVideoStream(id = "v2160", height = 2160, format = MediaFormat.WEBM, codec = "vp9", fps = 60, bitrate = 18_000_000),
            fakeVideoStream(id = "v1440", height = 1440, format = MediaFormat.WEBM, codec = "vp9", fps = 60, bitrate = 10_000_000),
            fakeVideoStream(id = "v1080", height = 1080, format = MediaFormat.MPEG_4, codec = "avc1.640028", fps = 30, bitrate = 4_000_000),
            fakeVideoStream(id = "v720", height = 720, format = MediaFormat.MPEG_4, codec = "avc1.4d401f", fps = 30, bitrate = 2_000_000),
            fakeVideoStream(id = "v480", height = 480, format = MediaFormat.MPEG_4, codec = "avc1.4d401e", fps = 30, bitrate = 1_000_000),
            fakeVideoStream(id = "v360", height = 360, format = MediaFormat.MPEG_4, codec = "avc1.4d401e", fps = 30, bitrate = 600_000),
            fakeVideoStream(id = "v240", height = 240, format = MediaFormat.MPEG_4, codec = "avc1.4d4015", fps = 30, bitrate = 300_000),
            fakeVideoStream(id = "v144", height = 144, format = MediaFormat.MPEG_4, codec = "avc1.4d400c", fps = 30, bitrate = 150_000),
        )

        assertEquals("v1080", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Auto)?.id)
        assertEquals("v2160", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Highest)?.id)
        assertEquals("v144", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Fixed(144))?.id)
        assertEquals("v240", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Fixed(240))?.id)
        assertEquals("v360", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Fixed(360))?.id)
        assertEquals("v480", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Fixed(480))?.id)
        assertEquals("v720", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Fixed(720))?.id)
        assertEquals("v1080", StreamSelection.selectVideoStream(candidates, VideoQualityMode.Fixed(1080))?.id)
    }

    @Test
    fun selectVideoStreamBreaksSameHeightTiesByAvcMpeg4ThenFpsAndBitrate() {
        val webmHighBitrate = fakeVideoStream(
            id = "webm-720-60",
            height = 720,
            format = MediaFormat.WEBM,
            codec = "vp9",
            fps = 60,
            bitrate = 5_000_000,
        )
        val avc30 = fakeVideoStream(
            id = "avc-720-30",
            height = 720,
            format = MediaFormat.MPEG_4,
            codec = "avc1.4d401f",
            fps = 30,
            bitrate = 2_000_000,
        )
        val avc60 = fakeVideoStream(
            id = "avc-720-60",
            height = 720,
            format = MediaFormat.MPEG_4,
            codec = "avc1.4d4020",
            fps = 60,
            bitrate = 3_500_000,
        )

        assertEquals(
            "avc-720-60",
            StreamSelection.selectVideoStream(listOf(webmHighBitrate, avc30, avc60), VideoQualityMode.Fixed(720))?.id,
        )

        val mp4Av1 = fakeVideoStream(
            id = "av01-240",
            height = 240,
            format = MediaFormat.MPEG_4,
            codec = "av01.0.00M.08",
            fps = 30,
            bitrate = 500_000,
        )
        val webm240 = fakeVideoStream(
            id = "webm-240",
            height = 240,
            format = MediaFormat.WEBM,
            codec = "vp9",
            fps = 30,
            bitrate = 500_000,
        )
        assertEquals(
            "av01-240",
            StreamSelection.selectVideoStream(listOf(webm240, mp4Av1), VideoQualityMode.Fixed(240))?.id,
        )
    }

    @Test
    fun qualityPreferenceScopesVideoOverridePerVideoWithoutMutatingLockedDefault() {
        QualityPreference.setDefault(VideoQualityMode.Fixed(240))
        assertEquals(VideoQualityMode.Fixed(240), QualityPreference.defaultMode)
        assertEquals(VideoQualityMode.Fixed(240), QualityPreference.effectiveFor("videoA"))
        assertEquals(VideoQualityMode.Fixed(240), QualityPreference.effectiveFor("videoB"))

        // Mid-video manual switch applies only to videoA
        QualityPreference.setVideoOverride("videoA", VideoQualityMode.Fixed(720))
        assertEquals(VideoQualityMode.Fixed(240), QualityPreference.defaultMode)
        assertEquals(VideoQualityMode.Fixed(720), QualityPreference.effectiveFor("videoA"))
        assertEquals(VideoQualityMode.Fixed(240), QualityPreference.effectiveFor("videoB"))
        assertEquals(720, QualityPreference.selectedHeightForUi("videoA", listOf(1080, 720, 480, 240, 144)))
        assertEquals(240, QualityPreference.selectedHeightForUi("videoB", listOf(1080, 720, 480, 240, 144)))

        // Highest & Auto UI resolution indicators
        QualityPreference.setVideoOverride("videoA", VideoQualityMode.Highest)
        assertEquals(1440, QualityPreference.selectedHeightForUi("videoA", listOf(1440, 1080, 720)))
        QualityPreference.setVideoOverride("videoA", VideoQualityMode.Auto)
        assertNull(QualityPreference.selectedHeightForUi("videoA", listOf(1440, 1080, 720)))

        // Clearing override restores locked default for videoA
        QualityPreference.clearVideoOverride()
        assertEquals(VideoQualityMode.Fixed(240), QualityPreference.effectiveFor("videoA"))
        assertEquals(240, QualityPreference.selectedHeightForUi("videoA", listOf(1080, 720, 480, 240, 144)))
    }

    private fun fakeVideoStream(
        id: String,
        height: Int,
        format: MediaFormat,
        codec: String,
        fps: Int,
        bitrate: Int,
        isVideoOnly: Boolean = true,
    ): VideoStream {
        val itagType = if (isVideoOnly) ItagItem.ItagType.VIDEO_ONLY else ItagItem.ItagType.VIDEO
        val itag = ItagItem(1000 + height, itagType, format, "${height}p", fps).apply {
            setHeight(height)
            setWidth((height * 16) / 9)
            setCodec(codec)
            setBitrate(bitrate)
        }
        return VideoStream.Builder()
            .setId(id)
            .setContent("https://example.com/video/$id", true)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setMediaFormat(format)
            .setIsVideoOnly(isVideoOnly)
            .setResolution("${height}p")
            .setItagItem(itag)
            .build()
    }
}
