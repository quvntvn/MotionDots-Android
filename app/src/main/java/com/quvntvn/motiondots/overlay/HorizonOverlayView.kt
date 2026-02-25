package com.quvntvn.motiondots.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.withSave
import kotlin.math.abs

class HorizonOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var opacity: Float = 0.16f
    private var intensity: Float = 5f
    private var shiftY: Float = 0f
    private var tiltDeg: Float = 0f

    fun configure(opacity: Float, intensity: Float) {
        setOpacity(opacity)
        setIntensity(intensity)
    }

    fun setOpacity(opacity: Float) {
        val newOpacity = opacity.coerceIn(0f, 1f)
        if (this.opacity == newOpacity) return
        this.opacity = newOpacity
        invalidate()
    }

    fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0f, 10f)
    }

    fun setMotion(verticalShiftPx: Float, tiltDegrees: Float) {
        val intensityScale = (intensity / 10f).coerceIn(0f, 1f)
        val scaledShiftY = (verticalShiftPx * intensityScale).coerceIn(-120f, 120f)
        val scaledTilt = (tiltDegrees * intensityScale).coerceIn(-12f, 12f)
        if (abs(shiftY - scaledShiftY) < 0.2f && abs(tiltDeg - scaledTilt) < 0.08f) return

        shiftY = scaledShiftY
        tiltDeg = scaledTilt
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)

        val centerX = width / 2f
        val centerY = height / 2f + shiftY

        canvas.withSave {
            rotate(tiltDeg, centerX, centerY)
            drawLine(0f, centerY, width.toFloat(), centerY, paint)
        }
    }
}
