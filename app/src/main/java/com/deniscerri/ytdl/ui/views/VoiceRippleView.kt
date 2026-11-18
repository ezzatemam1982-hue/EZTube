package com.deniscerri.ytdl.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.color.MaterialColors
import kotlin.math.min

/**
 * Concentric pulsing ripple waveform view that dynamically reacts in real-time
 * to SpeechRecognizer RMSdB audio levels and maintains a gentle breathing pulse when idle.
 */
class VoiceRippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val rippleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val rippleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var currentRmsScale = 0f
    private var targetRmsScale = 0f
    private var rmsAnimator: ValueAnimator? = null
    private var idlePulseAnimator: ValueAnimator? = null
    private var idlePulseScale = 0f
    private var isListening = false

    init {
        startIdlePulse()
    }

    fun setListening(listening: Boolean) {
        if (isListening == listening) return
        isListening = listening
        if (!listening) {
            rmsAnimator?.cancel()
            currentRmsScale = 0f
            targetRmsScale = 0f
        }
        invalidate()
    }

    fun updateRms(rmsdB: Float) {
        if (!isListening) return
        // SpeechRecognizer rmsdB typically ranges from -2f to 10f+; normalize to 0..1f
        val normalized = (rmsdB.coerceIn(0f, 10f) / 10f)
        targetRmsScale = normalized
        rmsAnimator?.cancel()
        rmsAnimator = ValueAnimator.ofFloat(currentRmsScale, targetRmsScale).apply {
            duration = 90
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentRmsScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startIdlePulse() {
        idlePulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                idlePulseScale = it.animatedValue as Float
                if (isListening && currentRmsScale < 0.1f) {
                    invalidate()
                }
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rmsAnimator?.cancel()
        idlePulseAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val minDim = min(width, height) / 2f
        val baseRadius = minDim * 0.40f
        val maxExpand = minDim * 0.55f

        val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#E53935"))
        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        if (isListening) {
            val combinedScale = (currentRmsScale * 0.85f + (if (currentRmsScale < 0.1f) idlePulseScale * 0.25f else 0f)).coerceIn(0f, 1f)

            // 3 Concentric Ripples expanding dynamically with voice energy
            for (i in 1..3) {
                val fraction = i / 3f
                val ringRadius = baseRadius + (maxExpand * combinedScale * fraction)
                val alpha = ((1f - (fraction * 0.65f)) * (0.20f + combinedScale * 0.45f) * 255).toInt().coerceIn(0, 255)

                // Concentric soft glow fill
                rippleFillPaint.color = Color.argb(alpha / 5, r, g, b)
                canvas.drawCircle(cx, cy, ringRadius, rippleFillPaint)

                // Concentric boundary ripple stroke
                rippleStrokePaint.strokeWidth = 2.5f * resources.displayMetrics.density
                rippleStrokePaint.color = Color.argb(alpha, r, g, b)
                canvas.drawCircle(cx, cy, ringRadius, rippleStrokePaint)
            }
        }
    }
}
