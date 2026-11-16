package com.clipflow.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSpeedToggleTest {

    @Test
    fun quickSpeedCycleAndLabelsMatchExpectedPresets() {
        assertEquals("1.0x", PlaybackSpeed.quickLabel(1.0f))
        assertEquals("1.25x", PlaybackSpeed.quickLabel(1.25f))
        assertEquals("1.5x", PlaybackSpeed.quickLabel(1.5f))
        assertEquals("2.0x", PlaybackSpeed.quickLabel(2.0f))

        assertEquals(1.25f, PlaybackSpeed.nextQuickSpeed(1.0f), 0.001f)
        assertEquals(1.5f, PlaybackSpeed.nextQuickSpeed(1.25f), 0.001f)
        assertEquals(2.0f, PlaybackSpeed.nextQuickSpeed(1.5f), 0.001f)
        assertEquals(1.0f, PlaybackSpeed.nextQuickSpeed(2.0f), 0.001f)
        assertEquals(1.0f, PlaybackSpeed.nextQuickSpeed(1.75f), 0.001f)
    }

    @Test
    fun playerStateCycleQuickSpeedUpdatesSpeedSequentially() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = PlayerState(scope)
            assertEquals(1.0f, state.speed, 0.001f)

            assertEquals(1.25f, state.cycleQuickSpeed(), 0.001f)
            assertEquals("1.25x", PlaybackSpeed.quickLabel(state.speed))

            assertEquals(1.5f, state.cycleQuickSpeed(), 0.001f)
            assertEquals("1.5x", PlaybackSpeed.quickLabel(state.speed))

            assertEquals(2.0f, state.cycleQuickSpeed(), 0.001f)
            assertEquals("2.0x", PlaybackSpeed.quickLabel(state.speed))

            assertEquals(1.0f, state.cycleQuickSpeed(), 0.001f)
            assertEquals("1.0x", PlaybackSpeed.quickLabel(state.speed))
        } finally {
            scope.cancel()
        }
    }
}
