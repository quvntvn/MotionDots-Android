package com.example.motiondots.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.withSave

class HorizonOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var opacity: Float = 0.16f
    private var shiftY: Float = 0f
    private var tiltDeg: Float = 0f

    fun configure(opacity: Float) {
        this.opacity = opacity.coerceIn(0.1f, 0.25f)
        invalidate()
    }

    fun setMotion(verticalShiftPx: Float, tiltDegrees: Float) {
        shiftY = verticalShiftPx.coerceIn(-120f, 120f)
        tiltDeg = tiltDegrees.coerceIn(-12f, 12f)
        postInvalidateOnAnimation()
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
