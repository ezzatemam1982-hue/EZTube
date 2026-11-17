package com.clipflow.app.player

import androidx.compose.ui.text.style.TextAlign
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.util.AppPreferenceDefaults
import com.deniscerri.ytdl.util.UpdateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsAndDefaultsOverhaulTest {

    @Test
    fun unifiedThemesDefaultToYouTubeSystemAmoledAndIncludeCuratedSwatches() {
        assertEquals("#FF0033", SEED_YOUTUBE)
        assertEquals(listOf("YouTube", "Cream Rose", "Erica"), CLIPFLOW_THEMES.map { it.name })
        assertTrue(CLIPFLOW_THEMES.all { it.nameRes != 0 })
        assertEquals(SEED_YOUTUBE, ThemeSettings.seed)
        assertEquals("system", ThemeSettings.mode)
        assertTrue(ThemeSettings.amoled)
        assertEquals(TextAlign.Start, OwnDirectionText.textAlign)
    }

    @Test
    fun playbackAndCaptionsSmartDefaultsMatchV103Spec() {
        assertTrue(PlaybackSpeed.musicAtNormal)
        assertTrue(AutoPip.on)
        assertTrue(FullscreenZoom.fill)
        assertFalse(DataSaver.enabled)
        assertEquals(480, DataSaver.MAX_HEIGHT)
        assertEquals(CaptionSettings.Show.Never, CaptionSettings.show)
    }

    @Test
    fun sponsorBlockCategoriesAllExposeLocalizedHelpDescriptions() {
        for (category in SponsorBlock.Category.entries) {
            assertTrue("Expected non-zero descRes for ${category.key}", category.descRes != 0)
        }
    }

    @Test
    fun legacyClipFlowPathsMigrateOnlyNonCustomizedDefaultDirectories() {
        assertTrue(AppPreferenceDefaults.isLegacyClipFlowPath("/storage/emulated/0/Download/ClipFlow/Audio", "/Audio"))
        assertTrue(AppPreferenceDefaults.isLegacyClipFlowPath("/storage/emulated/0/Download/ClipFlow/Video/", "/Video"))
        assertTrue(AppPreferenceDefaults.isLegacyClipFlowPath("/storage/emulated/0/Downloads/ClipFlow/Command", "/Command"))
        assertTrue(AppPreferenceDefaults.isLegacyClipFlowPath("/storage/emulated/0/Download/ClipFlow/Backups", "/Backups"))

        assertFalse(AppPreferenceDefaults.isLegacyClipFlowPath("/storage/emulated/0/Download/EZTube/Audio", "/Audio"))
        assertFalse(AppPreferenceDefaults.isLegacyClipFlowPath("/storage/emulated/0/Music/Custom", "/Audio"))
        assertFalse(AppPreferenceDefaults.isLegacyClipFlowPath("content://com.android.externalstorage.documents/tree/primary%3AMovies", "/Video"))
    }

    @Test
    fun innerTubeLocaleTagAndRegionFollowSelectedLanguage() {
        assertEquals("ar", AppLanguage.innerTubeLanguageTagFor(Locale.forLanguageTag("ar")))
        assertEquals("id", AppLanguage.innerTubeLanguageTagFor(Locale.forLanguageTag("id")))
        assertEquals("zh-TW", AppLanguage.innerTubeLanguageTagFor(Locale.forLanguageTag("zh-TW")))
        assertEquals("en", AppLanguage.innerTubeLanguageTagFor(null))

        assertEquals("SA", AppLanguage.innerTubeRegionFor(Locale.forLanguageTag("ar-SA"), Locale.US))
        assertEquals("EG", AppLanguage.innerTubeRegionFor(Locale.forLanguageTag("ar"), Locale.forLanguageTag("en-EG")))
        assertEquals("US", AppLanguage.innerTubeRegionFor(Locale.forLanguageTag("ar"), Locale.forLanguageTag("ar")))
    }

    @Test
    fun localizedArabicViewsAgesLockupsChipsAndChannelTabsParseCleanly() {
        assertEquals(1_200_000L, InnerTube.parseViews("١٫٢ مليون مشاهدة"))
        assertEquals(320_000L, InnerTube.parseViews("320 ألف مشاهدة"))
        assertEquals(0L, InnerTube.parseViews("بدون مشاهدات"))
        assertEquals(1_234_567L, InnerTube.parseViews("1,234,567 views"))
        assertEquals(1_200_000L, InnerTube.parseViews("1.2M views"))

        assertEquals(3 * 3_600_000L, NewVideos.ageMs("قبل ٣ ساعات"))
        assertEquals(2 * 3_600_000L, NewVideos.ageMs("منذ ساعتين"))
        assertEquals(3 * 3_600_000L, NewVideos.ageMs("3 hours ago"))

        val lockupJson = JSONObject(
            """
            {
              "contentId": "dQw4w9WgXcQ",
              "metadata": {
                "lockupMetadataViewModel": {
                  "title": { "content": "تجربة البث المباشر" },
                  "metadata": {
                    "contentMetadataViewModel": {
                      "metadataRows": [
                        { "metadataParts": [ { "text": { "content": "قناة التجربة" } } ] },
                        { "metadataParts": [ { "text": { "content": "١٫٢ مليون مشاهدة" } }, { "text": { "content": "قبل ٣ ساعات" } } ] }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val parsed = InnerTube.parseLockup(lockupJson)
        assertNotNull(parsed)
        assertEquals("قناة التجربة", parsed!!.channel)
        assertEquals(1_200_000L, parsed.views)
        assertEquals("قبل ٣ ساعات", parsed.uploaded)

        val chipsRoot = JSONObject(
            """
            {
              "feedFilterChipBarRenderer": {
                "contents": [
                  { "chipCloudChipRenderer": { "text": { "runs": [{ "text": "الكل" }] }, "isSelected": true } },
                  { "chipCloudChipRenderer": { "text": { "runs": [{ "text": "جديد لك" }] }, "isSelected": false, "navigationEndpoint": { "continuationCommand": { "token": "tok_new" } } } },
                  { "chipCloudChipRenderer": { "text": { "runs": [{ "text": "مباشر" }] }, "isSelected": false, "navigationEndpoint": { "continuationCommand": { "token": "tok_live" } } } },
                  { "chipCloudChipRenderer": { "text": { "runs": [{ "text": "موجز الفيديو المخصّص" }] }, "isSelected": false, "navigationEndpoint": { "continuationCommand": { "token": "tok_custom" } } } }
                ]
              }
            }
            """.trimIndent(),
        )
        val chips = InnerTube.parseHomeChips(chipsRoot)
        assertEquals(listOf("الكل" to null, "جديد لك" to "tok_new"), chips.map { it.title to it.token })

        val channelRoot = JSONObject().put(
            "contents",
            JSONObject().put(
                "twoColumnBrowseResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray()
                        .put(
                            JSONObject().put(
                                "tabRenderer",
                                JSONObject()
                                    .put("title", "الصفحة الرئيسية")
                                    .put("selected", true)
                                    .put("endpoint", JSONObject().put("browseEndpoint", JSONObject().put("params", "EghmZWF0dXJlZPIGBAoCMgA%3D")))
                                    .put("content", JSONObject()),
                            ),
                        )
                        .put(
                            JSONObject().put(
                                "tabRenderer",
                                JSONObject()
                                    .put("title", "الفيديوهات")
                                    .put("selected", false)
                                    .put("endpoint", JSONObject().put("browseEndpoint", JSONObject().put("params", "EgZ2aWRlb3PyBgQKAjoA"))),
                            ),
                        ),
                ),
            ),
        )
        val home = InnerTube.parseChannelHome(channelRoot, "UC1234567890123456789012")
        assertTrue(ChannelTab.Home in home.tabs)
        assertTrue(ChannelTab.Videos in home.tabs)
    }

    @Test
    fun avatarUrlNormalizationPrependsHttpsOnlyForProtocolRelativeUrls() {
        assertEquals("https://yt3.ggpht.com/avatar=s96", LocalAvatarStore.normalizeGoogleAvatarUrl("//yt3.ggpht.com/avatar=s96"))
        assertEquals("https://yt3.ggpht.com/avatar=s96", LocalAvatarStore.normalizeGoogleAvatarUrl("https://yt3.ggpht.com/avatar=s96"))
        assertNull(LocalAvatarStore.normalizeGoogleAvatarUrl("   "))
        assertNull(LocalAvatarStore.normalizeGoogleAvatarUrl(null))
    }

    @Test
    fun cartoonAvatarPresetsAndGridCountsMatchLayoutDesign() {
        val presets = LocalAvatarStore.Preset.entries
        assertEquals(8, presets.size)
        assertEquals(8, presets.map { it.drawableRes }.distinct().size)
        assertTrue(presets.all { it.drawableRes != 0 && LocalAvatarStore.Preset.of(it.id) == it })
        assertEquals(6, SeekStep.choices.size)
        assertEquals(4, CaptionSettings.Look.entries.size)
    }

    @Test
    fun silentYtdlpStartupUpdateEnforcesTwentyFourHourCooldown() {
        val dayMs = UpdateUtil.YTDLP_SILENT_UPDATE_INTERVAL_MS
        assertEquals(24L * 60L * 60L * 1000L, dayMs)
        assertTrue(UpdateUtil.isSilentYtdlpUpdateDue(lastCheckedMs = 0L, nowMs = 1_000L))
        assertFalse(UpdateUtil.isSilentYtdlpUpdateDue(lastCheckedMs = 10_000L, nowMs = 10_000L + dayMs - 1L))
        assertTrue(UpdateUtil.isSilentYtdlpUpdateDue(lastCheckedMs = 10_000L, nowMs = 10_000L + dayMs))
    }

    @Test
    fun hubBackCallbackOnlyInterceptsWhenHubHasBackStackOrCameFromStreaming() {
        assertFalse(MainActivity.shouldEnableHubBackCallback(inHub = false, hasNavBackStack = true, openedFromStream = true))
        assertFalse(MainActivity.shouldEnableHubBackCallback(inHub = false, hasNavBackStack = false, openedFromStream = false))
        assertFalse(MainActivity.shouldEnableHubBackCallback(inHub = true, hasNavBackStack = false, openedFromStream = false))

        assertTrue(MainActivity.shouldEnableHubBackCallback(inHub = true, hasNavBackStack = true, openedFromStream = false))
        assertTrue(MainActivity.shouldEnableHubBackCallback(inHub = true, hasNavBackStack = false, openedFromStream = true))
        assertTrue(MainActivity.shouldEnableHubBackCallback(inHub = true, hasNavBackStack = true, openedFromStream = true))
    }

    @Test
    fun playerStateCloseClearsPlaybackFlagsOverlaysAndRecoveryState() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = PlayerState(scope)
            state.expanded = true
            state.fullscreen = true
            state.lockScreen()
            state.sheet = PlayerSheet.Comments
            state.actionMessage = "Queued"
            StreamRecovery.recovering = "abc123"
            QualityPreference.setVideoOverride("abc123", VideoQualityMode.Fixed(720))

            state.close()

            assertNull(state.current)
            assertNull(state.details)
            assertTrue(state.queue.isEmpty())
            assertFalse(state.expanded)
            assertFalse(state.fullscreen)
            assertFalse(state.screenLocked)
            assertNull(state.sheet)
            assertNull(state.error)
            assertFalse(state.botCheck)
            assertFalse(state.loading)
            assertFalse(state.isPlaying)
            assertFalse(state.isBuffering)
            assertEquals(0L, state.positionMs)
            assertEquals(0L, state.bufferedMs)
            assertEquals(0L, state.durationMs)
            assertNull(state.actionMessage)
            assertNull(state.playingFormat)
            assertNull(state.decodedHeight)
            assertNull(StreamRecovery.recovering)
            assertNull(StreamRecovery.failed)
            assertNull(QualityPreference.videoOverride)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun silentYtdlpStartupUpdateDelaysEightSecondsAndRunsBackgroundWrapperCleanly() {
        assertEquals(8_000L, UpdateUtil.YTDLP_SILENT_UPDATE_DELAY_MS)
        val computed = UpdateUtil.runAtBackgroundPriority { 42 }
        assertEquals(42, computed)
    }

    @Test
    fun uniqueVideoCardKeysDisambiguateDuplicateIdsDeterministically() {
        val sample = listOf("v1", "v2", "v1", "v3", "v1", "v2").mapIndexed { idx, id ->
            VideoItem(
                id = id,
                title = "Title $idx",
                channel = "Channel",
                channelUrl = null,
                thumbnail = null,
                avatar = null,
                durationSec = 60,
                views = 100,
                uploaded = null,
            )
        }
        assertEquals(listOf("v1", "v2", "v1#1", "v3", "v1#2", "v2#1"), uniqueVideoCardKeys(sample))
        assertEquals(emptyList<String>(), uniqueVideoCardKeys(emptyList()))
    }
}
