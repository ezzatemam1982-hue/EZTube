package com.clipflow.app.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.deniscerri.ytdl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class FullscreenResizeModeTest {

    @Test
    fun fullscreenResizeModeMapsToFillAndFitWithoutZoomCrop() {
        val activeMode = fullscreenResizeMode(fill = true)
        val originalMode = fullscreenResizeMode(fill = false)

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, activeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, originalMode)
        assertNotEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, activeMode)
    }

    @Test
    fun fullscreenResizeLabelResMatchesActiveAndOriginalStates() {
        assertEquals(R.string.video_stretch_to_fill, fullscreenResizeLabelRes(fill = true))
        assertEquals(R.string.video_resize_original, fullscreenResizeLabelRes(fill = false))
    }

    @Test
    fun stretchScalesWideScreenFillsWidthWithoutCroppingTopOrBottom() {
        // 20:9 fullscreen stage (2400x1080) with 16:9 aspect-fitted video child (1920x1080).
        val fit = stretchScales(
            stageW = 2400f,
            stageH = 1080f,
            childW = 1920f,
            childH = 1080f,
            aspect = 16f / 9f,
            zoom = 0f,
        )
        assertEquals(1f, fit.scaleX, 1e-4f)
        assertEquals(1f, fit.scaleY, 1e-4f)

        val fill = stretchScales(
            stageW = 2400f,
            stageH = 1080f,
            childW = 1920f,
            childH = 1080f,
            aspect = 16f / 9f,
            zoom = 1f,
        )
        assertEquals(1.25f, fill.scaleX, 1e-4f)
        assertEquals(1f, fill.scaleY, 1e-4f)
        assertEquals(2400f, 1920f * fill.scaleX, 0.5f)
        assertEquals(1080f, 1080f * fill.scaleY, 0.5f)
    }

    @Test
    fun stretchScalesNarrowerScreenFillsHeightWithoutCroppingSides() {
        // 16:9 stage (1920x1080) with 21:9 aspect-fitted child (1920x823).
        val fill = stretchScales(
            stageW = 1920f,
            stageH = 1080f,
            childW = 1920f,
            childH = 823f,
            aspect = 21f / 9f,
            zoom = 1f,
        )
        assertEquals(1f, fill.scaleX, 1e-4f)
        assertEquals(1080f / 823f, fill.scaleY, 1e-4f)
        assertEquals(1920f, 1920f * fill.scaleX, 0.5f)
        assertEquals(1080f, 823f * fill.scaleY, 0.5f)
    }

    @Test
    fun stretchScalesHandlesMidpointOvershootUndershootAndMatchedAspect() {
        val mid = stretchScales(2400f, 1080f, 1920f, 1080f, 16f / 9f, zoom = 0.5f)
        assertEquals(1.125f, mid.scaleX, 1e-4f)
        assertEquals(1f, mid.scaleY, 1e-4f)

        val undershoot = stretchScales(2400f, 1080f, 1920f, 1080f, 16f / 9f, zoom = -0.2f)
        assertEquals(undershoot.scaleX, undershoot.scaleY, 1e-4f)
        assertTrue(undershoot.scaleX in 0.5f..1f)

        val overshoot = stretchScales(2400f, 1080f, 1920f, 1080f, 16f / 9f, zoom = 1.05f)
        assertTrue(overshoot.scaleX > 1.25f)
        assertTrue(overshoot.scaleY > 1f)

        val matched = stretchScales(1920f, 1080f, 1920f, 1080f, 16f / 9f, zoom = 1f)
        assertEquals(1f, matched.scaleX, 1e-4f)
        assertEquals(1f, matched.scaleY, 1e-4f)

        val zeroChildFallback = stretchScales(2400f, 1080f, 0f, 0f, 16f / 9f, zoom = 1f)
        assertEquals(1.25f, zeroChildFallback.scaleX, 1e-4f)
        assertEquals(1f, zeroChildFallback.scaleY, 1e-4f)

        val invalid = stretchScales(0f, 1080f, 0f, 0f, 16f / 9f, zoom = 1f)
        assertEquals(1f, invalid.scaleX, 1e-4f)
        assertEquals(1f, invalid.scaleY, 1e-4f)
    }

    @Test
    fun threeModeAspectRatioCyclesFitFillZoomAndMapsLabels() {
        assertEquals(FullscreenAspectMode.Fill, FullscreenAspectMode.Fit.next())
        assertEquals(FullscreenAspectMode.Zoom, FullscreenAspectMode.Fill.next())
        assertEquals(FullscreenAspectMode.Fit, FullscreenAspectMode.Zoom.next())

        assertEquals(R.string.video_aspect_fit, fullscreenResizeLabelRes(FullscreenAspectMode.Fit))
        assertEquals(R.string.video_aspect_fill, fullscreenResizeLabelRes(FullscreenAspectMode.Fill))
        assertEquals(R.string.video_aspect_zoom, fullscreenResizeLabelRes(FullscreenAspectMode.Zoom))
    }

    @Test
    fun resolveFullscreenScaleHandlesFitFillZoomAndClampedPinch() {
        val fit = resolveFullscreenScale(FullscreenScaleSelection(FullscreenAspectMode.Fit), 2400f, 1080f, 16f / 9f)
        assertEquals(1f, fit, 1e-4f)

        val fill = resolveFullscreenScale(FullscreenScaleSelection(FullscreenAspectMode.Fill), 2400f, 1080f, 16f / 9f)
        assertEquals(1.25f, fill, 1e-4f)

        val zoom = resolveFullscreenScale(FullscreenScaleSelection(FullscreenAspectMode.Zoom), 2400f, 1080f, 16f / 9f)
        assertEquals(1.25f * ZOOM_MODE_MULTIPLIER, zoom, 1e-4f)

        val pinched = resolveFullscreenScale(
            FullscreenScaleSelection(FullscreenAspectMode.Fill, pinchScale = 1.65f),
            2400f,
            1080f,
            16f / 9f,
        )
        assertEquals(1.65f, pinched, 1e-4f)

        val clampedMax = resolveFullscreenScale(
            FullscreenScaleSelection(FullscreenAspectMode.Fill, pinchScale = 3.5f),
            2400f,
            1080f,
            16f / 9f,
        )
        assertEquals(MAX_PINCH_SCALE, clampedMax, 1e-4f)
    }

    @Test
    fun pinchZoomReleaseSnapsBelowOneBackToFitAndClampsBounds() {
        assertEquals(null, resolveReleasedPinchZoom(0.95f))
        assertEquals(null, resolveReleasedPinchZoom(1.00f))
        assertEquals(1.4f, resolveReleasedPinchZoom(1.4f)!!, 1e-4f)
        assertEquals(MAX_PINCH_SCALE, resolveReleasedPinchZoom(2.6f)!!, 1e-4f)

        assertTrue(interactivePinchScale(0.8f) in 0.94f..1.0f)
        assertTrue(interactivePinchScale(2.5f) > MAX_PINCH_SCALE)
    }
}
