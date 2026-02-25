package com.quvntvn.motiondots.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.withSave
import com.quvntvn.motiondots.data.DotColor
import kotlin.math.abs
import kotlin.random.Random

class DotsOverlayView(context: Context) : View(context) {

    enum class DotMode { CLASSIC, EDGE }

    private data class Dot(val x: Float, val y: Float, val radius: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val random = Random(System.currentTimeMillis())
    private var dots: List<Dot> = emptyList()

    private var dotCount: Int = 40
    private var opacity: Float = 0.16f
    private var intensity: Float = 5f
    private var mode: DotMode = DotMode.CLASSIC
    private var dotRadiusScale: Float = 1f

    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    fun configure(dotCount: Int, opacity: Float, mode: DotMode, intensity: Float, dotRadiusScale: Float, dotColor: DotColor) {
        this.mode = mode
        setDotCount(dotCount)
        setOpacity(opacity)
        setIntensity(intensity)
        setDotSizeScale(dotRadiusScale)
        setDotColor(dotColor)
    }

    fun setOpacity(opacity: Float) {
        val newOpacity = opacity.coerceIn(0f, 1f)
        if (this.opacity == newOpacity) return
        this.opacity = newOpacity
        invalidate()
    }

    fun setDotCount(count: Int) {
        val newCount = count.coerceIn(10, 100)
        if (dotCount == newCount && dots.isNotEmpty()) return
        dotCount = newCount
        regenerateDots()
    }

    fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0f, 10f)
    }

    fun setMode(mode: DotMode) {
        if (this.mode == mode) return
        this.mode = mode
        regenerateDots()
    }

    fun setDotSizeScale(scale: Float) {
        val sanitized = scale.coerceIn(0.5f, 2f)
        if (dotRadiusScale == sanitized) return
        dotRadiusScale = sanitized
        regenerateDots()
    }

    fun setDotColor(color: DotColor) {
        val target = if (color == DotColor.BLACK) Color.BLACK else Color.WHITE
        if (paint.color == target) return
        paint.color = target
        invalidate()
    }

    fun setMotion(dx: Float, dy: Float) {
        val intensityScale = (intensity / 10f).coerceIn(0f, 1f)
        val scaledX = (dx * intensityScale).coerceIn(-60f, 60f)
        val scaledY = (dy * intensityScale).coerceIn(-60f, 60f)
        val movementThreshold = 0.2f

        if (abs(offsetX - scaledX) < movementThreshold && abs(offsetY - scaledY) < movementThreshold) {
            return
        }

        offsetX = scaledX
        offsetY = scaledY
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        regenerateDots()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)

        canvas.withSave {
            translate(offsetX, offsetY)
            for (dot in dots) {
                drawCircle(dot.x, dot.y, dot.radius, paint)
            }
        }
    }

    private fun regenerateDots() {
        if (width <= 0 || height <= 0) {
            dots = emptyList()
            invalidate()
            return
        }
        dots = buildDots(width = width.toFloat(), height = height.toFloat())
        invalidate()
    }

    private fun buildDots(width: Float, height: Float): List<Dot> {
        val list = ArrayList<Dot>(dotCount)
        val sideBand = width * 0.2f

        repeat(dotCount) {
            val x = when (mode) {
                DotMode.CLASSIC -> random.nextFloat() * width
                DotMode.EDGE -> {
                    if (random.nextBoolean()) {
                        random.nextFloat() * sideBand
                    } else {
                        width - (random.nextFloat() * sideBand)
                    }
                }
            }
            val y = random.nextFloat() * height
            val radius = (5f + random.nextFloat() * 8f) * dotRadiusScale
            list += Dot(x, y, radius)
        }
        return list
    }
}
