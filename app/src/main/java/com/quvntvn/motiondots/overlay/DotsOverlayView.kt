package com.quvntvn.motiondots.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.withSave
import kotlin.math.abs
import kotlin.random.Random

class DotsOverlayView(context: Context) : View(context) {

    enum class DotMode { CLASSIC, EDGE }

    private data class Dot(val x: Float, val y: Float, val radius: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())
    private var dots: List<Dot> = emptyList()
    private var initialized = false

    private var dotCount: Int = 40
    private var opacity: Float = 0.16f
    private var mode: DotMode = DotMode.CLASSIC

    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    private var shouldAnimate = false

    fun configure(dotCount: Int, opacity: Float, mode: DotMode) {
        this.dotCount = dotCount.coerceIn(10, 100)
        this.opacity = opacity.coerceIn(0.1f, 0.2f)
        this.mode = mode
        initialized = false
        invalidate()
    }

    fun setMotion(dx: Float, dy: Float) {
        val movementThreshold = 0.4f
        if (abs(dx) < movementThreshold && abs(dy) < movementThreshold) {
            shouldAnimate = false
            return
        }
        shouldAnimate = true
        offsetX = dx.coerceIn(-60f, 60f)
        offsetY = dy.coerceIn(-60f, 60f)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!initialized && width > 0 && height > 0) {
            dots = buildDots()
            initialized = true
        }

        paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)

        canvas.withSave {
            translate(offsetX, offsetY)
            for (dot in dots) {
                drawCircle(dot.x, dot.y, dot.radius, paint)
            }
        }

        if (shouldAnimate) {
            postInvalidateOnAnimation()
        }
    }

    private fun buildDots(): List<Dot> {
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
            val radius = 5f + random.nextFloat() * 8f
            list += Dot(x, y, radius)
        }
        return list
    }
}
