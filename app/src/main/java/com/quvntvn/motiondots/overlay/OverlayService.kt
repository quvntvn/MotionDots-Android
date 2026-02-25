package com.quvntvn.motiondots.overlay

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
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.quvntvn.motiondots.data.DensityPreset
import com.quvntvn.motiondots.data.IntensityPreset
import com.quvntvn.motiondots.data.OpacityPreset
import com.quvntvn.motiondots.data.OverlayMode
import com.quvntvn.motiondots.data.OverlaySettings
import com.quvntvn.motiondots.data.SettingsDataStore
import com.quvntvn.motiondots.data.SizePreset
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
    private var lastSensorDispatchMs = 0L

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
                applySettings(settings)
            }
        }
    }

    private fun applySettings(settings: OverlaySettings) {
        val previous = currentSettings
        currentSettings = settings

        val modeChanged = previous.selectedMode != settings.selectedMode || overlayView == null
        if (modeChanged) {
            swapOverlayForMode(settings.selectedMode)
        }

        val mappedIntensity = mapIntensityPreset(settings.intensityPreset)
        val mappedOpacity = mapOpacityPreset(settings.opacityPreset)

        when (val view = overlayView) {
            is DotsOverlayView -> {
                val targetMode = if (settings.selectedMode == OverlayMode.EDGE_DOTS) {
                    DotsOverlayView.DotMode.EDGE
                } else {
                    DotsOverlayView.DotMode.CLASSIC
                }
                view.setMode(targetMode)
                view.setOpacity(mappedOpacity)
                view.setDotColor(settings.dotColor)
                view.setDotCount(mapDensityPreset(settings.densityPreset))
                view.setDotSizeScale(mapSizePreset(settings.sizePreset))
                view.setIntensity(mappedIntensity)
            }

            is HorizonOverlayView -> {
                view.setOpacity(mappedOpacity)
                view.setIntensity(mappedIntensity)
                view.setDotColor(settings.dotColor)
            }
        }

        if (settings.selectedMode == OverlayMode.DISABLED) {
            unregisterSensor()
        } else {
            registerSensorIfNeeded()
        }
    }

    private fun swapOverlayForMode(mode: OverlayMode) {
        removeOverlayViewSafely()

        val view = when (mode) {
            OverlayMode.CLASSIC_DOTS -> DotsOverlayView(this)
            OverlayMode.EDGE_DOTS -> DotsOverlayView(this)
            OverlayMode.HORIZON -> HorizonOverlayView(this)
            OverlayMode.DISABLED -> null
        }

        overlayView = view
        if (view != null) {
            addOverlayView(view)
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

        val now = SystemClock.elapsedRealtime()
        if (now - lastSensorDispatchMs < 16L) return
        lastSensorDispatchMs = now

        if (abs(filteredX) < 0.03f && abs(filteredY) < 0.03f) return

        val dx = -filteredX * 38f
        val dy = filteredY * 38f

        serviceScope.launch {
            when (val view = overlayView) {
                is DotsOverlayView -> {
                    view.setMotion(dx = dx, dy = dy)
                }

                is HorizonOverlayView -> {
                    val verticalShift = dy * 1.3f
                    val tilt = -dx * 1.8f
                    view.setMotion(verticalShiftPx = verticalShift, tiltDegrees = tilt)
                }
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

    private fun mapIntensityPreset(preset: IntensityPreset): Float = when (preset) {
        IntensityPreset.LOW -> 3f
        IntensityPreset.NORMAL -> 6f
        IntensityPreset.HIGH -> 9f
    }

    private fun mapOpacityPreset(preset: OpacityPreset): Float = when (preset) {
        OpacityPreset.SUBTLE -> 0.14f
        OpacityPreset.BALANCED -> 0.24f
        OpacityPreset.VISIBLE -> 0.38f
    }

    private fun mapDensityPreset(preset: DensityPreset): Int = when (preset) {
        DensityPreset.LIGHT -> 24
        DensityPreset.STANDARD -> 48
        DensityPreset.DENSE -> 80
    }

    private fun mapSizePreset(preset: SizePreset): Float = when (preset) {
        SizePreset.SMALL -> 0.75f
        SizePreset.MEDIUM -> 1f
        SizePreset.LARGE -> 1.35f
    }
}
