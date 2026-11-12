package com.example.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import com.example.audio.AudioReactiveManager
import com.example.data.WallpaperRepository
import com.example.gl.SceneRenderer
import com.example.model.ShaderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Production Android Live Wallpaper Service for DepthLive.
 * Renders real-time multi-layer parallax scenes with dynamic shaders and ambient reactivity.
 */
class DepthLiveWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "DepthLiveWallpaper"
    }

    override fun onCreateEngine(): Engine {
        return DepthLiveEngine()
    }

    inner class DepthLiveEngine : WallpaperService.Engine(), SensorEventListener {

        private var glSurfaceView: WallpaperGLSurfaceView? = null
        private lateinit var renderer: SceneRenderer

        private var sensorManager: SensorManager? = null
        private var rotationSensor: Sensor? = null
        private var accelSensor: Sensor? = null

        private var isVisibleState = false
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var audioObserverJob: Job? = null
        private var sceneObserverJob: Job? = null
        private var renderingActive = false
        private var powerManager: PowerManager? = null

        private val configListener: (ShaderConfig) -> Unit = { newConfig ->
            if (::renderer.isInitialized) {
                renderer.config = newConfig
                if (newConfig.audioReactive && renderingActive) {
                    AudioReactiveManager.startListening(applicationContext)
                } else if (!newConfig.audioReactive) {
                    AudioReactiveManager.stopListening()
                }
            }
        }

        // System Broadcast Receivers for Unlock & Battery
        private val systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Device unlocked (ACTION_USER_PRESENT)")
                        renderer.triggerUnlockAnimation()
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (level >= 0 && scale > 0) {
                            val percent = (level * 100) / scale
                            renderer.batteryPercent = percent
                            renderer.isLowBattery = percent < 20
                        }
                    }
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> updateRenderingState()
                }
            }
        }

        override fun onCreate(surfaceHolder: android.view.SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)

            WallpaperRepository.init(applicationContext)
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            renderer = SceneRenderer(applicationContext, WallpaperRepository.shaderConfig.value)
            renderer.currentScene = WallpaperRepository.activeScene.value

            glSurfaceView = WallpaperGLSurfaceView(applicationContext, surfaceHolder).apply {
                setRenderer(renderer)
                renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
                onPause()
            }

            // Sensors
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            // Register System Broadcasts safely
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(systemEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(systemEventReceiver, filter)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not register system receiver: ${e.message}")
            }

            // Observe scene and configuration changes for this engine's lifetime.
            sceneObserverJob = serviceScope.launch {
                WallpaperRepository.activeScene.collectLatest { scene ->
                    renderer.currentScene = scene
                }
            }
            WallpaperRepository.addConfigListener(configListener)

            audioObserverJob = serviceScope.launch {
                AudioReactiveManager.energy.collectLatest { energy ->
                    renderer.audioEnergy = energy
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            updateRenderingState()
        }

        private fun updateRenderingState() {
            val shouldRender = isVisibleState && powerManager?.isDeviceIdleMode != true
            if (shouldRender == renderingActive) return
            renderingActive = shouldRender
            if (shouldRender) {
                glSurfaceView?.onResume()
                registerSensors()
                if (renderer.config.audioReactive) {
                    AudioReactiveManager.startListening(applicationContext)
                }
            } else {
                glSurfaceView?.onPause()
                sensorManager?.unregisterListener(this)
                AudioReactiveManager.stopListening()
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            if (renderer.config.homeScreenScrollParallax) {
                renderer.homeScrollOffset = xOffset
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event == null) return

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val normX = event.x / max(1f, glSurfaceView?.width?.toFloat() ?: 1080f)
                    val normY = event.y / max(1f, glSurfaceView?.height?.toFloat() ?: 2400f)
                    renderer.triggerTouchRipple(normX, normY)
                }
            }
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultExpected: Boolean
        ): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) {
                val normX = x.toFloat() / max(1f, glSurfaceView?.width?.toFloat() ?: 1080f)
                val normY = y.toFloat() / max(1f, glSurfaceView?.height?.toFloat() ?: 2400f)
                renderer.triggerTouchRipple(normX, normY)
            }
            return super.onCommand(action, x, y, z, extras, resultExpected)
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || !renderingActive) return

            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)

                    val roll = orientation[2] * (180.0f / Math.PI.toFloat())
                    val pitch = orientation[1] * (180.0f / Math.PI.toFloat())
                    renderer.tiltX = min(4.0f, max(-4.0f, roll * 0.15f))
                    renderer.tiltY = min(4.0f, max(-4.0f, pitch * 0.15f))
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    renderer.tiltX = min(4.0f, max(-4.0f, -ax * 0.4f))
                    renderer.tiltY = min(4.0f, max(-4.0f, ay * 0.4f))
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun registerSensors() {
            val sm = sensorManager ?: return
            if (rotationSensor != null) {
                sm.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            } else if (accelSensor != null) {
                sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        override fun onDestroy() {
            WallpaperRepository.removeConfigListener(configListener)
            serviceScope.cancel()
            sensorManager?.unregisterListener(this)
            AudioReactiveManager.stopListening()
            try {
                unregisterReceiver(systemEventReceiver)
            } catch (ignored: Exception) {}

            glSurfaceView?.onDestroy()
            glSurfaceView = null
            super.onDestroy()
        }
    }
}
