package com.example.motiondots.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class OverlayService : Service() {

    private lateinit var sensorManager: SensorManager
    private var accelListener: SensorEventListener? = null
    private lateinit var windowManager: WindowManager
    private var overlayView: DotsOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = DotsOverlayView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayView, params)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelListener = object : SensorEventListener {
            // petit filtre low-pass
            var fx = 0f
            var fy = 0f

            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values[0] // gauche/droite
                val ay = event.values[1] // haut/bas

                val alpha = 0.1f
                fx = fx + alpha * (ax - fx)
                fy = fy + alpha * (ay - fy)

                // facteur pour conversion en pixels (à tuner)
                val scale = 6f
                overlayView?.setMotion(dx = -fx * scale, dy = fy * scale)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            accelListener,
            accel,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    override fun onDestroy() {
        accelListener?.let { sensorManager.unregisterListener(it) }
        accelListener = null
        super.onDestroy()
        overlayView?.let {
            windowManager.removeView(it)
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}