package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.audio.AudioReactiveManager
import com.example.gl.SceneRenderer
import com.example.model.ProcessedScene
import com.example.model.ShaderConfig
import kotlinx.coroutines.delay

@Composable
fun GlLivePreview(
    scene: ProcessedScene?,
    config: ShaderConfig,
    modifier: Modifier = Modifier,
    showFpsHud: Boolean = true
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audioEnergy by AudioReactiveManager.energy.collectAsState()
    val renderer = remember(context) { SceneRenderer(context.applicationContext) }
    val glView = remember(context, renderer) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    renderer.triggerTouchRipple(
                        event.x / view.width.coerceAtLeast(1),
                        event.y / view.height.coerceAtLeast(1)
                    )
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    view.performClick()
                }
                true
            }
            onPause()
        }
    }

    DisposableEffect(context, lifecycle, glView) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var running = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val matrix = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, orientation)
                    val degrees = 180f / Math.PI.toFloat()
                    renderer.tiltX = (orientation[2] * degrees * 0.15f).coerceIn(-4f, 4f)
                    renderer.tiltY = (orientation[1] * degrees * 0.15f).coerceIn(-4f, 4f)
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    renderer.tiltX = (-event.values[0] * 0.4f).coerceIn(-4f, 4f)
                    renderer.tiltY = (event.values[1] * 0.4f).coerceIn(-4f, 4f)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun updateRunningState() {
            val shouldRun = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !power.isDeviceIdleMode
            if (running == shouldRun) return
            running = shouldRun
            if (shouldRun) {
                glView.onResume()
                sensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            } else {
                sensors.unregisterListener(listener)
                glView.onPause()
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (level >= 0 && scale > 0) {
                            val percent = level * 100 / scale
                            renderer.batteryPercent = percent
                            renderer.isLowBattery = percent < 20
                        }
                    }
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> updateRunningState()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        val observer = LifecycleEventObserver { _, _ -> updateRunningState() }
        lifecycle.addObserver(observer)
        updateRunningState()

        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
            sensors.unregisterListener(listener)
            if (running) glView.onPause()
        }
    }

    var fps by remember { mutableFloatStateOf(0f) }
    var frameTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycle, renderer, showFpsHud) {
        if (showFpsHud) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    fps = renderer.currentFps
                    frameTime = renderer.frameRenderTimeMs
                    delay(500)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
            .background(Color(0xFF0F172A))
            .testTag("live_gl_preview_box")
    ) {
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize().testTag("interactive_gl_preview"),
            update = {
                if (renderer.currentScene !== scene) renderer.currentScene = scene
                renderer.config = config
                renderer.audioEnergy = audioEnergy
            }
        )
        if (scene == null) {
            CircularProgressIndicator(
                color = Color(0xFF38BDF8),
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
        }
        if (showFpsHud && scene != null) {
            Text(
                text = " ${String.format("%.0f", fps)} FPS • ${String.format("%.1f", frameTime)}ms ",
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .background(Color(0xDD0B0F17), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFF10B981),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                .background(Color(0x99000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
            Text(" Tap to ripple • Tilt to parallax", color = Color(0xFFE2E8F0), fontSize = 10.sp)
        }
    }
}
