package com.clipflow.app.player

import androidx.compose.ui.text.style.TextDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionsDeduplicationAndRtlTest {

    @Test
    fun srv3ParseDeduplicatesIdenticalOverlappingCues() {
        val xml = """
            <body>
              <p t="1000" d="3000">Welcome to ClipFlow.</p>
              <p t="1200" d="3500">Welcome  to  ClipFlow.</p>
            </body>
        """.trimIndent()

        val cues = Srv3.parse(xml, timedWords = false)
        assertEquals(1, cues.size)
        assertEquals(1000L, cues.single().startMs)
        assertEquals(4700L, cues.single().endMs)
        assertEquals("Welcome to ClipFlow.", cues.single().text)
    }

    @Test
    fun srv3ParseClampsRollingWindowPrefixCues() {
        val xml = """
            <body>
              <p t="1000" d="3000">مرحبا بكم</p>
              <p t="2000" d="3000">مرحبا بكم في التطبيق.</p>
            </body>
        """.trimIndent()

        val cues = Srv3.parse(xml, timedWords = false)
        assertEquals(2, cues.size)
        assertEquals(listOf(CaptionLine("مرحبا بكم", false)), captionLinesAt(cues, 1500L))
        assertEquals(listOf(CaptionLine("مرحبا بكم في التطبيق.", false)), captionLinesAt(cues, 2500L))
    }

    @Test
    fun captionLinesAtDeduplicatesRepeatedStackedTranslatedLines() {
        val cues = deduplicateCues(
            listOf(
                CaptionCue(startMs = 0L, endMs = 4000L, text = "هذا نص مترجم.\nهذا نص مترجم."),
                CaptionCue(startMs = 500L, endMs = 4500L, text = "هذا  نص مترجم."),
            ),
        )

        val visible = captionLinesAt(cues, 1500L)
        assertEquals(listOf(CaptionLine("هذا نص مترجم.", false)), visible)
    }

    @Test
    fun captionPagesDoesNotStackDuplicateOrRepeatedOverlappingLines() {
        val cues = listOf(
            CaptionCue(startMs = 0L, endMs = 1500L, text = "السطر الأول."),
            CaptionCue(startMs = 1600L, endMs = 3000L, text = "السطر الأول."),
            CaptionCue(startMs = 3100L, endMs = 4500L, text = "السطر الأول.\nالسطر الثاني."),
        )

        val pages = captionPages(cues)
        assertEquals(listOf(CaptionLine("السطر الأول.", false)), captionPageAt(pages, 1700L))

        val overlappingDedup = deduplicateCaptionLines(
            listOf(
                CaptionLine("Line A\nLine B", sound = false),
                CaptionLine("Line B\nLine C", sound = false),
            ),
        )
        assertEquals(
            listOf(
                CaptionLine("Line A", sound = false),
                CaptionLine("Line B", sound = false),
                CaptionLine("Line C", sound = false),
            ),
            overlappingDedup,
        )
    }

    @Test
    fun resolveCaptionTextDirectionUsesRtlForArabicAndMixedArabicLines() {
        assertEquals(
            TextDirection.Rtl,
            resolveCaptionTextDirection(listOf("مرحبا بالعالم."), languageTag = "en"),
        )
        assertEquals(
            TextDirection.Rtl,
            resolveCaptionTextDirection(listOf("ClipFlow يعمل بشكل ممتاز."), languageTag = null),
        )
        assertEquals(
            TextDirection.Rtl,
            resolveCaptionTextDirection(listOf("2026."), languageTag = "ar-EG"),
        )
        assertEquals(
            TextDirection.Ltr,
            resolveCaptionTextDirection(listOf("Hello world."), languageTag = "en-US"),
        )
    }

    @Test
    fun parseAudioTrackIdExtractsMergedMediaPeriodPrefixedIds() {
        assertEquals("en.4", parseAudioTrackId("${AUDIO_TRACK_ID_PREFIX}en.4"))
        assertEquals("ar-EG.2", parseAudioTrackId("1:${AUDIO_TRACK_ID_PREFIX}ar-EG.2"))
        assertEquals(null, parseAudioTrackId("1:251"))
        assertEquals(null, parseAudioTrackId(AUDIO_TRACK_ID_PREFIX))
        assertEquals(null, parseAudioTrackId(null))
    }
}
