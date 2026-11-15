package com.example.segmentation

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gl.SceneRenderer
import com.example.model.LayerType
import com.example.model.ParsedLayer
import com.example.model.ParticleType
import com.example.model.ProcessedScene
import com.example.model.ShaderConfig
import java.nio.ByteBuffer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterRippleRenderingTest {
    @Test
    fun tapRippleChangesOnlyVisibleWater() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY)
        check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
        val config = requireNotNull(configs[0])
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(context != EGL14.EGL_NO_CONTEXT)
        val surface = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 64, EGL14.EGL_HEIGHT, 64, EGL14.EGL_NONE), 0
        )
        check(surface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, surface, surface, context))
        try {
            val source = IntArray(64 * 64) { i ->
                val x = i % 64
                val y = i / 64
                Color.rgb((x * 29) % 256, (y * 17) % 256, ((x + y) * 13) % 256)
            }
            fun bitmap(pixels: IntArray) = Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888)
            val original = bitmap(source)
            val layers = LayerType.entries.mapIndexed { index, type ->
                val data = IntArray(source.size) { i ->
                    val x = i % 64
                    val subject = x in 40..43
                    if ((index < 4 && x / 16 == index && !subject) || (type == LayerType.FOREGROUND && subject)) {
                        source[i]
                    } else Color.TRANSPARENT
                }
                ParsedLayer(type, bitmap(data), type.defaultDepth, 25f, data.count { it != Color.TRANSPARENT })
            }
            val renderer = SceneRenderer(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                this.config = ShaderConfig(
                    cloudDriftSpeed = 0f, celestialSunTracking = false, skyLightShift = 0f,
                    waterRippleAmplitude = 0f, causticShimmer = 0f,
                    foregroundSwayAmplitude = 0f, parallaxDegrees = 0f,
                    touchRippleIntensity = 3f, particleType = ParticleType.NONE
                )
                currentScene = ProcessedScene("test", "test", original, original, layers, 64, 64)
                onSurfaceCreated(null, null)
                onSurfaceChanged(null, 64, 64)
            }
            fun frame(): ByteArray {
                renderer.onDrawFrame(null)
                val pixels = ByteBuffer.allocateDirect(64 * 64 * 4)
                GLES20.glReadPixels(0, 0, 64, 64, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
                return ByteArray(pixels.capacity()).also { pixels.get(it) }
            }
            val baseline = frame()
            renderer.triggerTouchRipple(0.1f, 0.5f)
            Thread.sleep(100)
            val nonWater = frame()
            assertTrue("A non-water tap changed the image", baseline.contentEquals(nonWater))
            renderer.triggerTouchRipple(0.65f, 0.5f)
            Thread.sleep(100)
            assertTrue("A subject tap started a water ripple", baseline.contentEquals(frame()))
            renderer.triggerTouchRipple(0.55f, 0.5f)
            Thread.sleep(150)
            val water = frame()
            var changedWaterPixels = 0
            for (y in 0 until 64) for (x in 0 until 64) {
                val offset = (y * 64 + x) * 4
                val changed = (0..3).any { baseline[offset + it] != water[offset + it] }
                if (x in 33..46 && x !in 40..43 && changed) changedWaterPixels++
                if (x < 30 || x > 49 || x in 40..43) {
                    assertTrue("Ripple changed a non-water pixel at $x,$y", !changed)
                }
            }
            assertTrue("Water did not visibly ripple", changedWaterPixels > 10)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
