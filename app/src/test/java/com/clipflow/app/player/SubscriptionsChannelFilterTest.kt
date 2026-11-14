package com.clipflow.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionsChannelFilterTest {

    private fun channel(id: String, name: String) = ChannelRef(
        id = id,
        name = name,
        avatar = null,
    )

    private fun video(id: String, channelName: String, channelUrl: String? = null, title: String = "Video $id") =
        VideoItem(
            id = id,
            title = title,
            channel = channelName,
            channelUrl = channelUrl,
            thumbnail = null,
            avatar = null,
            durationSec = 120L,
            views = 1_000L,
            uploaded = "1 day ago",
        )

    @Test
    fun normalizeChannelQueryHandlesEnglishAndArabicDiacritics() {
        assertEquals("mkbhd", normalizeChannelQuery("  MKBHD  "))
        assertEquals("cafe", normalizeChannelQuery("Caf\u00E9"))
        assertEquals("اخبار التقنيه", normalizeChannelQuery("  أَخْبَـارُ   التّقنية  "))
        assertEquals("اسلام", normalizeChannelQuery("إِسْلام"))
        assertEquals("القران الكريم", normalizeChannelQuery("ٱلْقُرْآنُ الكَرِيمُ"))
        assertEquals("موسيقي", normalizeChannelQuery("موسيقى"))
    }

    @Test
    fun filterSubscribedChannelsReturnsAllOnBlankQuery() {
        val channels = listOf(
            channel("UC1", "Veritasium"),
            channel("UC2", "الدحيح"),
        )
        assertEquals(channels, filterSubscribedChannels(channels, ""))
        assertEquals(channels, filterSubscribedChannels(channels, "   "))
    }

    @Test
    fun filterSubscribedChannelsMatchesEnglishAndArabicCaseAndDiacriticInsensitively() {
        val veritasium = channel("UC1", "Veritasium")
        val dahih = channel("UC2", "الدَّحِيح - El Da7ee7")
        val techNews = channel("UC3", "أخبار التّقنية")
        val channels = listOf(veritasium, dahih, techNews)

        assertEquals(listOf(veritasium), filterSubscribedChannels(channels, "veri"))
        assertEquals(listOf(veritasium), filterSubscribedChannels(channels, "VERITASIUM"))
        assertEquals(listOf(dahih), filterSubscribedChannels(channels, "الدحيح"))
        assertEquals(listOf(techNews), filterSubscribedChannels(channels, "اخبار التقنية"))
        assertTrue(filterSubscribedChannels(channels, "nonexistent").isEmpty())
    }

    @Test
    fun filterSubscriptionVideosMatchesByChannelIdUrlAndFallbackName() {
        val mkbhd = channel("UCBJycsmduvYEL83R_U4JriQ", "Marques Brownlee")
        val arabicCh = channel("UCArabic123", "أحمد أبو زيد")
        val channels = listOf(mkbhd, arabicCh)

        // First video has abbreviated channel name ("MKBHD") but exact channelUrl ID matching Marques Brownlee.
        val vByUrl = video(
            id = "v1",
            channelName = "MKBHD",
            channelUrl = "https://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ?sub_confirmation=1",
        )
        // Second video has no channelUrl and relies on channel name fallback.
        val vByArabicName = video(
            id = "v2",
            channelName = "أَحْمَد أَبُو زَيْد",
            channelUrl = null,
        )
        // Unrelated video whose title contains search term should NOT match channel filter.
        val vUnrelated = video(
            id = "v3",
            channelName = "Other Creator",
            channelUrl = "https://www.youtube.com/channel/UCOther999",
            title = "Marques review",
        )
        val videos = listOf(vByUrl, vByArabicName, vUnrelated)

        assertEquals(videos, filterSubscriptionVideos(videos, channels, "  "))
        assertEquals(listOf(vByUrl), filterSubscriptionVideos(videos, channels, "marques"))
        assertEquals(listOf(vByArabicName), filterSubscriptionVideos(videos, channels, "احمد ابو"))
    }
}
