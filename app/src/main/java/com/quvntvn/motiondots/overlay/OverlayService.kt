package com.example.motiondots.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.motiondots.data.OverlayMode
import com.example.motiondots.data.OverlaySettings
import com.example.motiondots.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var sensorManager: SensorManager
    private lateinit var settingsDataStore: SettingsDataStore

    private var overlayView: View? = null
    private var sensor: Sensor? = null
    private var receiverRegistered = false

    private var currentSettings = OverlaySettings()

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null

    private var filteredX = 0f
    private var filteredY = 0f

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> unregisterSensor()
                Intent.ACTION_SCREEN_ON -> registerSensorIfNeeded()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        settingsDataStore = SettingsDataStore(this)

        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        registerScreenReceiver()
        observeSettingsAndApply()
    }

    private fun observeSettingsAndApply() {
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                switchOverlay(settings)
            }
        }
    }

    private fun switchOverlay(settings: OverlaySettings) {
        removeOverlayViewSafely()

        val view = when (settings.selectedMode) {
            OverlayMode.CLASSIC_DOTS -> DotsOverlayView(this).apply {
                configure(settings.dotCount, settings.opacity, DotsOverlayView.DotMode.CLASSIC)
            }

            OverlayMode.EDGE_DOTS -> DotsOverlayView(this).apply {
                configure(settings.dotCount, settings.opacity, DotsOverlayView.DotMode.EDGE)
            }

            OverlayMode.HORIZON -> HorizonOverlayView(this).apply {
                configure(settings.opacity)
            }

            OverlayMode.DISABLED -> null
        }

        overlayView = view
        if (view != null) {
            addOverlayView(view)
            registerSensorIfNeeded()
        } else {
            unregisterSensor()
        }
    }

    private fun addOverlayView(view: View) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        runCatching {
            if (view.parent == null) {
                windowManager.addView(view, params)
            }
        }
    }

    private fun removeOverlayViewSafely() {
        overlayView?.let { view ->
            runCatching {
                if (view.parent != null) {
                    windowManager.removeView(view)
                }
            }
        }
        overlayView = null
    }

    private fun registerSensorIfNeeded() {
        if (overlayView == null || sensor == null) return
        sensorManager.unregisterListener(this)
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterSensor() {
        sensorManager.unregisterListener(this)
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
        receiverRegistered = true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 2) return

        val alpha = 0.18f
        filteredX += alpha * (values[0] - filteredX)
        filteredY += alpha * (values[1] - filteredY)

        if (abs(filteredX) < 0.03f && abs(filteredY) < 0.03f) return

        val intensityScale = (currentSettings.intensity / 10f).coerceIn(0f, 1f)
        val pxScale = 38f * intensityScale

        when (val view = overlayView) {
            is DotsOverlayView -> {
                view.setMotion(dx = -filteredX * pxScale, dy = filteredY * pxScale)
            }

            is HorizonOverlayView -> {
                val verticalShift = filteredY * pxScale * 1.3f
                val tilt = filteredX * 1.8f * intensityScale * 10f
                view.setMotion(verticalShiftPx = verticalShift, tiltDegrees = tilt)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        unregisterSensor()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            receiverRegistered = false
        }
        removeOverlayViewSafely()
        settingsJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
