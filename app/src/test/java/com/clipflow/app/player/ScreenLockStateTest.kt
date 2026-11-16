package com.clipflow.app.player

import com.deniscerri.ytdl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLockStateTest {

    private fun withPlayerState(block: (PlayerState) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            block(PlayerState(scope))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun lockScreenActivatesLockAndDismissesOpenOverlaysAndPromptsTimeout() = withPlayerState { state ->
        assertFalse(state.screenLocked)
        state.sheet = PlayerSheet.Quality

        state.lockScreen()

        assertTrue(state.screenLocked)
        assertNull(state.sheet)
        assertNull(state.playlistTarget)
        assertFalse(state.holdingFast)
        assertEquals(2_500L, ScreenLockPromptHideMs)
    }

    @Test
    fun screenLockSurvivesFullscreenModeTogglesAndUnlocksExplicitly() = withPlayerState { state ->
        state.fullscreen = false
        state.lockScreen()
        assertTrue(state.screenLocked)

        // Orientation / fullscreen state toggles preserve lock until explicit unlock or player close.
        state.fullscreen = true
        assertTrue(state.screenLocked)
        state.fullscreen = false
        assertTrue(state.screenLocked)

        state.unlockScreen()
        assertFalse(state.screenLocked)
    }

    @Test
    fun closeResetsScreenLockAndAllPlayerUiState() = withPlayerState { state ->
        state.expanded = true
        state.fullscreen = true
        state.lockScreen()
        assertTrue(state.screenLocked)

        state.close()

        assertFalse(state.screenLocked)
        assertFalse(state.expanded)
        assertFalse(state.fullscreen)
        assertNull(state.sheet)
        assertNull(state.current)
    }

    @Test
    fun barToolsAlwaysExposeScreenLockAlongsideFullscreen() = withPlayerState { state ->
        val tools = barTools(state, pip = null)

        assertTrue(tools.contains(BarTool.Lock))
        assertTrue(tools.contains(BarTool.Fullscreen))
        assertTrue(tools.indexOf(BarTool.Lock) < tools.indexOf(BarTool.Fullscreen))
        assertTrue(R.string.bar_tool_lock != 0)
        assertTrue(R.string.video_lock_screen != 0)
        assertTrue(R.string.video_unlock != 0)
        assertTrue(R.string.video_screen_unlocked != 0)
    }
}
