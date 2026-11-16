package com.clipflow.app.player

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FeedStateAndSessionRecoveryTest {

    private fun sampleVideo(id: String) = VideoItem(
        id = id,
        title = "Video $id",
        channel = "Channel $id",
        channelUrl = "https://www.youtube.com/channel/UC_$id",
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        avatar = null,
        durationSec = 120,
        views = 1000,
        uploaded = "1 day ago",
    )

    @Test
    fun pullToRefreshInvokesLoaderAndPreservesVideosWhileSurfacingErrors() = runBlocking {
        var failNext = false
        var calls = 0
        val feed = FeedState(this, prefetchTop = 0, name = "HomeTest") { _ ->
            calls++
            if (failNext) throw IOException("YouTube test error")
            FeedPage(listOf(sampleVideo("vid1"), sampleVideo("vid2")), next = null)
        }

        feed.ensureLoaded()
        repeat(6) { yield() }
        assertEquals(1, calls)
        assertEquals(2, feed.videos.size)
        assertNull(feed.error)
        assertFalse(feed.loading)

        failNext = true
        feed.refresh()
        repeat(6) { yield() }
        assertEquals(2, calls)
        assertEquals(2, feed.videos.size)
        assertNotNull(feed.error)
        assertFalse(feed.loading)
        assertFalse(feed.refreshing)

        failNext = false
        feed.refresh()
        repeat(6) { yield() }
        assertEquals(3, calls)
        assertNull(feed.error)
        assertEquals(2, feed.videos.size)
    }

    @Test
    fun showOrRetryRefetchesWhenFeedIsEmptyOrErrored() = runBlocking {
        var calls = 0
        val feed = FeedState(this, prefetchTop = 0, name = "SubsTest") { _ ->
            calls++
            when (calls) {
                1 -> FeedPage(emptyList(), next = null, statusMessage = "Watch history is paused")
                2 -> FeedPage(listOf(sampleVideo("sub1")), next = null)
                else -> FeedPage(listOf(sampleVideo("sub1"), sampleVideo("sub2")), next = null)
            }
        }

        feed.ensureLoaded()
        repeat(6) { yield() }
        assertTrue(feed.loadedEmpty)
        assertEquals("Watch history is paused", feed.statusMessage)

        // Switching back to a tab whose feed is empty must actively retry.
        feed.showOrRetry()
        repeat(6) { yield() }
        assertEquals(2, calls)
        assertEquals(1, feed.videos.size)
        assertNull(feed.statusMessage)

        // Once populated and error-free, showOrRetry does not redundantly reload.
        feed.showOrRetry()
        repeat(6) { yield() }
        assertEquals(2, calls)
    }

    @Test
    fun loadFreshKeepsFirstPageIfContinuationThrows() = runBlocking {
        var phase = 0
        val feed = FeedState(this, prefetchTop = 0, freshOnRefresh = true, name = "HomeFresh") { next ->
            if (next == null) {
                phase++
                val ids = listOf("a1", "a2")
                FeedPage(ids.map(::sampleVideo), next = "cont_p2")
            } else {
                throw IOException("YouTube continuation failed")
            }
        }

        feed.ensureLoaded()
        repeat(6) { yield() }
        assertEquals(2, feed.videos.size)

        // Pull-to-refresh triggers loadFresh() because 'shown' already contains a1, a2.
        feed.refresh()
        repeat(8) { yield() }
        assertNull(feed.error)
        assertEquals(2, feed.videos.size)
    }
}
