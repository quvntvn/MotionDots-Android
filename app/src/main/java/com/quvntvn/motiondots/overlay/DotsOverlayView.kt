package com.example.motiondots.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.max
import kotlin.random.Random

class DotsOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // blanc très léger, tu ajusteras
        alpha = 35 // ~14% d'opacité
    }

    private val dots = ArrayList<Dot>()
    private var initialized = false

    // mouvement global (on le connectera aux capteurs après)
    private var offsetX = 0f
    private var offsetY = 0f

    data class Dot(var x: Float, var y: Float, val r: Float)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!initialized && width > 0 && height > 0) {
            initialized = true
            generateDots()
        }

        // fond transparent: rien à dessiner
        for (d in dots) {
            canvas.drawCircle(d.x + offsetX, d.y + offsetY, d.r, paint)
        }

        // redessine en boucle (simple MVP)
        postInvalidateOnAnimation()
    }

    private fun generateDots() {
        dots.clear()
        val count = 40 // ajuste
        val minR = 6f
        val maxR = 12f

        repeat(count) {
            dots.add(
                Dot(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    r = minR + Random.nextFloat() * (maxR - minR)
                )
            )
        }
    }

    fun setMotion(dx: Float, dy: Float) {
        // clamp pour éviter des déplacements énormes
        offsetX = dx.coerceIn(-40f, 40f)
        offsetY = dy.coerceIn(-40f, 40f)
    }
}