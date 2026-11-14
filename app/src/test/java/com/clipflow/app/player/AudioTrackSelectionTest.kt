package com.clipflow.app.player

import com.deniscerri.ytdl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.util.Locale

class AudioTrackSelectionTest {

    @Test
    fun buildAudioTrackGroupsOrdersOriginalFirstAndDefaultsToOriginalEvenWhenDubbedAppearsFirstOrMatchesAppLocale() {
        val arabicDub = fakeAudioStream(
            id = "ar-dub",
            format = MediaFormat.M4A,
            bitrate = 256,
            trackId = "ar.4",
            trackName = "Arabic dubbed",
            trackType = AudioTrackType.DUBBED,
            locale = Locale.forLanguageTag("ar"),
        )
        val spanishDub = fakeAudioStream(
            id = "es-dub",
            format = MediaFormat.M4A,
            bitrate = 192,
            trackId = "es.4",
            trackName = "Spanish dubbed",
            trackType = AudioTrackType.DUBBED,
            locale = Locale.forLanguageTag("es"),
        )
        val englishOriginal = fakeAudioStream(
            id = "en-orig",
            format = MediaFormat.M4A,
            bitrate = 128,
            trackId = "en.4",
            trackName = "English original",
            trackType = AudioTrackType.ORIGINAL,
            locale = Locale.ENGLISH,
        )

        val groups = StreamSelection.buildAudioTrackGroups(
            streams = listOf(arabicDub, spanishDub, englishOriginal),
            appLocale = Locale.forLanguageTag("ar"),
        )

        assertEquals(3, groups.size)
        val defaultGroup = groups.first()
        assertEquals("en.4", defaultGroup.option.id)
        assertEquals(AudioTrackKind.Original, defaultGroup.option.kind)
        assertTrue(defaultGroup.option.isDefault)
        assertEquals("en-orig", defaultGroup.bestStream.id)

        assertFalse(groups[1].option.isDefault)
        assertFalse(groups[2].option.isDefault)
        assertEquals(AudioTrackKind.Dubbed, groups[1].option.kind)
        assertEquals(AudioTrackKind.Dubbed, groups[2].option.kind)
    }

    @Test
    fun buildAudioTrackGroupsPrefersM4aOverWebmaAndHighestBitrateWithinLogicalTrack() {
        val webmaHighBitrate = fakeAudioStream(
            id = "webma-160",
            format = MediaFormat.WEBMA,
            bitrate = 160,
            trackId = "en.4",
            trackName = "English",
            trackType = AudioTrackType.ORIGINAL,
            locale = Locale.ENGLISH,
        )
        val m4aLowBitrate = fakeAudioStream(
            id = "m4a-96",
            format = MediaFormat.M4A,
            bitrate = 96,
            trackId = "en.4",
            trackName = "English",
            trackType = AudioTrackType.ORIGINAL,
            locale = Locale.ENGLISH,
        )
        val m4aBestBitrate = fakeAudioStream(
            id = "m4a-128",
            format = MediaFormat.M4A,
            bitrate = 128,
            trackId = "en.4",
            trackName = "English",
            trackType = AudioTrackType.ORIGINAL,
            locale = Locale.ENGLISH,
        )

        val groups = StreamSelection.buildAudioTrackGroups(
            streams = listOf(webmaHighBitrate, m4aLowBitrate, m4aBestBitrate),
            appLocale = Locale.ENGLISH,
        )

        assertEquals(1, groups.size)
        assertEquals("m4a-128", groups.single().bestStream.id)
    }

    @Test
    fun buildAudioTrackGroupsDisambiguatesRegionalVariantsAndLocalizesDisplayNames() {
        val originalEn = fakeAudioStream(
            id = "en",
            trackId = "en.1",
            trackType = AudioTrackType.ORIGINAL,
            locale = Locale.ENGLISH,
        )
        val dubbedAr = fakeAudioStream(
            id = "ar",
            trackId = "ar.1",
            trackType = AudioTrackType.DUBBED,
            locale = Locale.forLanguageTag("ar"),
        )
        val dubbedEsLatam = fakeAudioStream(
            id = "es-419",
            trackId = "es-419.1",
            trackName = "Spanish (Latin America)",
            trackType = AudioTrackType.DUBBED,
            locale = Locale.forLanguageTag("es-419"),
        )
        val dubbedEsSpain = fakeAudioStream(
            id = "es-ES",
            trackId = "es-ES.1",
            trackName = "Spanish (Spain)",
            trackType = AudioTrackType.DUBBED,
            locale = Locale.forLanguageTag("es-ES"),
        )

        val streams = listOf(dubbedAr, dubbedEsSpain, originalEn, dubbedEsLatam)

        val englishGroups = StreamSelection.buildAudioTrackGroups(streams, appLocale = Locale.ENGLISH)
        val byIdEn = englishGroups.associateBy { it.option.id }
        assertEquals("English", byIdEn.getValue("en.1").option.displayName)
        assertEquals("Arabic", byIdEn.getValue("ar.1").option.displayName)
        val latamName = byIdEn.getValue("es-419.1").option.displayName
        val spainName = byIdEn.getValue("es-ES.1").option.displayName
        assertNotEquals(latamName, spainName)
        assertNotEquals("Spanish", latamName)
        assertNotEquals("Spanish", spainName)

        val arabicLocale = Locale.forLanguageTag("ar")
        val arabicGroups = StreamSelection.buildAudioTrackGroups(streams, appLocale = arabicLocale)
        val byIdAr = arabicGroups.associateBy { it.option.id }
        assertEquals("العربية", byIdAr.getValue("ar.1").option.displayName)
        assertEquals(
            Locale.ENGLISH.getDisplayLanguage(arabicLocale).replaceFirstChar { it.titlecase(arabicLocale) },
            byIdAr.getValue("en.1").option.displayName,
        )
    }

    @Test
    fun buildAudioTrackGroupsInfersOriginalWhenAudioTrackTypeIsNull() {
        val singleNameless = fakeAudioStream(
            id = "single",
            trackId = null,
            trackName = null,
            trackType = null,
            locale = null,
        )
        val singleGroups = StreamSelection.buildAudioTrackGroups(listOf(singleNameless), Locale.ENGLISH)
        assertEquals(1, singleGroups.size)
        assertEquals(AudioTrackKind.Original, singleGroups.single().option.kind)
        assertTrue(singleGroups.single().option.isDefault)
        assertEquals("Original", singleGroups.single().option.displayName)

        val inferredOriginal = fakeAudioStream(
            id = "orig-inferred",
            trackId = "main",
            trackName = "Director audio - original",
            trackType = null,
            locale = null,
        )
        val inferredSecondary = fakeAudioStream(
            id = "sec-inferred",
            trackId = "alt",
            trackName = "French",
            trackType = null,
            locale = Locale.FRENCH,
        )
        val multiGroups = StreamSelection.buildAudioTrackGroups(
            listOf(inferredSecondary, inferredOriginal),
            appLocale = Locale.ENGLISH,
        )
        assertEquals(2, multiGroups.size)
        assertEquals("main", multiGroups[0].option.id)
        assertEquals(AudioTrackKind.Original, multiGroups[0].option.kind)
        assertEquals("Director audio", multiGroups[0].option.displayName)
        assertTrue(multiGroups[0].option.isDefault)

        assertEquals("alt", multiGroups[1].option.id)
        assertEquals(AudioTrackKind.Secondary, multiGroups[1].option.kind)
        assertFalse(multiGroups[1].option.isDefault)
    }

    @Test
    fun audioTrackKeyUsesTrimmedTrackIdOrStructuredFallback() {
        val withId = fakeAudioStream(id = "1", trackId = "  en.4  ", locale = Locale.ENGLISH)
        assertEquals("en.4", StreamSelection.audioTrackKey(withId))

        val withoutId = fakeAudioStream(
            id = "2",
            trackId = "   ",
            trackName = "  French  ",
            trackType = AudioTrackType.DUBBED,
            locale = Locale.FRENCH,
        )
        assertEquals("fr|DUBBED|French", StreamSelection.audioTrackKey(withoutId))

        val unspecified = fakeAudioStream(id = "3")
        assertEquals("und|UNSPECIFIED|", StreamSelection.audioTrackKey(unspecified))
    }

    @Test
    fun hasDubbedTracksReflectsDubbedKindOrMultipleBaseLanguages() {
        val enOriginal = AudioTrackOption("en.1", "en-US", "English", null, AudioTrackKind.Original, true)
        val enDescriptive = AudioTrackOption("en.2", "en-GB", "English", null, AudioTrackKind.Descriptive, false)
        val arDubbed = AudioTrackOption("ar.1", "ar", "Arabic", null, AudioTrackKind.Dubbed, false)
        val sameLangDubbed = AudioTrackOption("en.3", "en", "English", null, AudioTrackKind.Dubbed, false)
        val frSecondary = AudioTrackOption("fr.1", "fr", "French", null, AudioTrackKind.Secondary, false)

        assertFalse(hasDubbedTracks(emptyList()))
        assertFalse(hasDubbedTracks(listOf(enOriginal)))
        assertFalse(hasDubbedTracks(listOf(enOriginal, enDescriptive)))
        assertTrue(hasDubbedTracks(listOf(enOriginal, arDubbed)))
        assertTrue(hasDubbedTracks(listOf(enOriginal, sameLangDubbed)))
        assertTrue(hasDubbedTracks(listOf(enOriginal, frSecondary)))
    }

    @Test
    fun audioKindLabelResMapsAllKinds() {
        assertEquals(R.string.video_audio_kind_original, audioKindLabelRes(AudioTrackKind.Original))
        assertEquals(R.string.video_audio_kind_dubbed, audioKindLabelRes(AudioTrackKind.Dubbed))
        assertEquals(R.string.video_audio_kind_descriptive, audioKindLabelRes(AudioTrackKind.Descriptive))
        assertEquals(R.string.video_audio_kind_secondary, audioKindLabelRes(AudioTrackKind.Secondary))
    }

    private fun fakeAudioStream(
        id: String,
        url: String = "https://example.com/audio/$id",
        format: MediaFormat = MediaFormat.M4A,
        bitrate: Int = 128,
        trackId: String? = null,
        trackName: String? = null,
        trackType: AudioTrackType? = null,
        locale: Locale? = null,
    ): AudioStream = AudioStream.Builder()
        .setId(id)
        .setContent(url, true)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setMediaFormat(format)
        .setAverageBitrate(bitrate)
        .setAudioTrackId(trackId)
        .setAudioTrackName(trackName)
        .setAudioTrackType(trackType)
        .setAudioLocale(locale)
        .build()
}
