package com.example.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import com.example.model.LayerType
import com.example.model.ParticleType
import com.example.model.ProcessedScene
import com.example.model.ShaderConfig
import java.nio.FloatBuffer
import java.util.Calendar
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * High-performance standalone OpenGL ES 2.0 Live Scene Renderer.
 * Used identically inside both the interactive Jetpack Compose Preview and WallpaperService.Engine.
 */
class SceneRenderer(
    private val context: Context,
    var config: ShaderConfig = ShaderConfig()
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "SceneRenderer"

        // Fullscreen quad vertices (X, Y)
        private val QUAD_VERTICES = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )

        // Texture coordinates (U, V) - OpenGL Y is inverted relative to Android Bitmap
        private val QUAD_TEX_COORDS = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )
    }

    // GL Buffers
    private val vertexBuffer: FloatBuffer = GlShaderUtils.createFloatBuffer(QUAD_VERTICES)
    private val texCoordBuffer: FloatBuffer = GlShaderUtils.createFloatBuffer(QUAD_TEX_COORDS)

    // Current Scene & Textures
    var currentScene: ProcessedScene? = null
        set(value) {
            field = value
            pendingTextureReload = true
        }

    private val layerTextures = IntArray(5) // Index corresponds to LayerType ordinal
    private var pendingTextureReload = false

    // GL Programs
    private var skyProgram = 0
    private var waterProgram = 0
    private var foregroundProgram = 0
    private var defaultProgram = 0
    private var particleProgram = 0

    // Viewport dimensions
    private var viewWidth = 1080
    private var viewHeight = 2400

    // Timing & Frame Rate Governor
    private var startTimeMs = SystemClock.uptimeMillis()
    private var lastFrameTimeNs = System.nanoTime()
    var currentFps: Float = 60.0f
        private set
    var frameRenderTimeMs: Float = 0.0f
        private set

    // Interactive Sensor & Touch state
    var tiltX: Float = 0.0f // -4 to +4 degrees
    var tiltY: Float = 0.0f
    var homeScrollOffset: Float = 0.5f // 0.0 to 1.0

    // Touch ripples: up to 3 concurrent ripples
    data class Ripple(var x: Float, var y: Float, var birthTimeMs: Long, var active: Boolean = false)
    private val ripples = Array(3) { Ripple(0.5f, 0.5f, 0L) }

    // Unlock / Wake event
    private var unlockTriggerTimeMs = 0L
    private var isUnlockAnimating = false

    // Real-time Audio energy (0.0 to 1.0)
    var audioEnergy: Float = 0.0f

    // Battery & Thermal status
    var batteryPercent: Int = 100
    var isLowBattery: Boolean = false

    // Weather state
    var weatherCode: Int = 0 // 0=Clear, 61=Rain, 71=Snow, etc.
    var isRaining: Boolean = false
    var isSnowing: Boolean = false

    fun triggerTouchRipple(xNorm: Float, yNorm: Float) {
        val now = SystemClock.uptimeMillis()
        // Replace oldest or inactive ripple
        var slot = ripples.indexOfFirst { !it.active }
        if (slot == -1) {
            slot = ripples.indices.minByOrNull { ripples[it].birthTimeMs } ?: 0
        }
        ripples[slot].x = xNorm
        ripples[slot].y = yNorm
        ripples[slot].birthTimeMs = now
        ripples[slot].active = true
    }

    fun triggerUnlockAnimation() {
        unlockTriggerTimeMs = SystemClock.uptimeMillis()
        isUnlockAnimating = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.05f, 0.07f, 0.1f, 1.0f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        buildShaders()
        loadSceneTextures()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = max(1, width)
        viewHeight = max(1, height)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStartNs = System.nanoTime()

        // Adaptive frame rate limiter
        val targetFps = if (isLowBattery && config.batterySaverCoolPalette) {
            min(config.targetFps, 30)
        } else {
            config.targetFps
        }

        if (pendingTextureReload) {
            loadSceneTextures()
            pendingTextureReload = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val scene = currentScene
        if (scene == null) {
            // Draw placeholder background gradient
            drawFallbackScreen()
            return
        }

        val nowMs = SystemClock.uptimeMillis()
        val timeSec = (nowMs - startTimeMs) / 1000.0f

        // Calculate Sun angle (0 to 2*PI tracking real 24h clock)
        val cal = Calendar.getInstance()
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val sunAngle = (minutesOfDay / 1440.0f) * 2.0f * PI.toFloat()

        // Unlock progress (0.0 -> 1.0 over 800ms)
        var unlockProgress = 1.0f
        if (isUnlockAnimating) {
            val elapsed = nowMs - unlockTriggerTimeMs
            if (elapsed < config.unlockScatterDurationMs) {
                val t = elapsed.toFloat() / config.unlockScatterDurationMs
                // Elastic damping settle curve
                unlockProgress = 1.0f - (1.0f - t) * exp(-t * 3.5f) * cos(t * 10.0f)
            } else {
                isUnlockAnimating = false
                unlockProgress = 1.0f
            }
        }

        // Render each semantic layer in depth order (Far -> Near)
        val layers = scene.layers.sortedBy { it.depth }
        for (layer in layers) {
            if (!layer.isEnabled) continue

            val textureId = layerTextures[layer.type.ordinal]
            if (textureId == 0) continue

            val program = when (layer.type) {
                LayerType.SKY -> skyProgram
                LayerType.WATER -> waterProgram
                LayerType.FOREGROUND -> foregroundProgram
                else -> defaultProgram
            }

            GLES20.glUseProgram(program)

            // Bind attributes
            val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(texHandle)
            GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            // Common uniforms
            setUniform1f(program, "uTime", timeSec)
            setUniform1f(program, "uLayerDepth", layer.depth)
            setUniform2f(program, "uTilt", (tiltX / 4.0f) * config.gyroscopeSensitivity, (tiltY / 4.0f) * config.gyroscopeSensitivity)
            setUniform1f(program, "uParallaxScale", (config.parallaxDegrees / 4.0f) * 0.05f)
            setUniform1f(program, "uHomeScroll", homeScrollOffset - 0.5f)
            setUniform1f(program, "uAudioEnergy", if (config.audioReactive) audioEnergy * config.audioSensitivity else 0.0f)
            setUniform1f(program, "uUnlockProgress", unlockProgress)

            // Touch ripples (center and age)
            val r = ripples[0]
            val age0 = if (r.active) (nowMs - r.birthTimeMs) / 1000.0f else 999.0f
            setUniform2f(program, "uRippleCenter", r.x, r.y)
            setUniform1f(program, "uRippleAge", age0)
            setUniform1f(program, "uRippleIntensity", config.touchRippleIntensity)

            // AMOLED & Battery Cooler
            setUniform1i(program, "uAmoledMode", if (config.amoledMode) 1 else 0)
            setUniform1f(program, "uAmoledThreshold", config.amoledThreshold)
            setUniform1i(program, "uBatteryCooler", if (isLowBattery && config.batterySaverCoolPalette) 1 else 0)

            // Layer-specific uniforms
            when (layer.type) {
                LayerType.SKY -> {
                    setUniform1f(program, "uCloudSpeed", config.cloudDriftSpeed)
                    setUniform1f(program, "uSunAngle", if (config.celestialSunTracking) sunAngle else 0.0f)
                    setUniform1f(program, "uSkyLightShift", config.skyLightShift)
                }
                LayerType.WATER -> {
                    setUniform1f(program, "uWaterSpeed", config.waterRippleSpeed)
                    setUniform1f(program, "uWaterAmp", config.waterRippleAmplitude)
                    setUniform1f(program, "uWaveFreq", config.waterWaveFrequency)
                    setUniform1f(program, "uCaustic", config.causticShimmer)
                }
                LayerType.FOREGROUND -> {
                    setUniform1f(program, "uSwaySpeed", config.foregroundSwaySpeed)
                    setUniform1f(program, "uSwayAmp", config.foregroundSwayAmplitude)
                }
                else -> { /* standard layer */ }
            }

            // Bind Layer Texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            setUniform1i(program, "uTexture", 0)

            // Draw Layer Quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(posHandle)
            GLES20.glDisableVertexAttribArray(texHandle)
        }

        // Draw particle overlay (dust, fireflies, rain, snow)
        if (config.particleType != ParticleType.NONE || isRaining || isSnowing) {
            renderAmbientParticles(timeSec, unlockProgress)
        }

        // Track FPS and frame time
        val frameEndNs = System.nanoTime()
        frameRenderTimeMs = (frameEndNs - frameStartNs) / 1_000_000.0f
        val deltaNs = frameEndNs - lastFrameTimeNs
        lastFrameTimeNs = frameEndNs
        if (deltaNs > 0) {
            val instantFps = 1_000_000_000.0f / deltaNs
            currentFps = currentFps * 0.9f + instantFps * 0.1f // smooth EMA
        }

        // Throttle frame time to respect battery budget (e.g., 30fps target)
        if (targetFps < 60) {
            val frameBudgetMs = (1000 / targetFps).toLong()
            val elapsedMs = (frameEndNs - frameStartNs) / 1_000_000L
            val sleepMs = frameBudgetMs - elapsedMs
            if (sleepMs > 2) {
                try {
                    Thread.sleep(sleepMs)
                } catch (ignored: InterruptedException) {}
            }
        }
    }

    private fun renderAmbientParticles(timeSec: Float, unlockProgress: Float) {
        if (particleProgram == 0) return

        GLES20.glUseProgram(particleProgram)
        val posHandle = GLES20.glGetAttribLocation(particleProgram, "aPosition")
        val texHandle = GLES20.glGetAttribLocation(particleProgram, "aTexCoord")

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        setUniform1f(particleProgram, "uTime", timeSec)
        setUniform1f(particleProgram, "uUnlockProgress", unlockProgress)
        setUniform1f(particleProgram, "uAudioEnergy", if (config.audioReactive) audioEnergy else 0.0f)

        val particleCode = when {
            isRaining || config.particleType == ParticleType.RAIN_DROPS -> 3
            isSnowing || config.particleType == ParticleType.SNOW_FLAKES -> 4
            config.particleType == ParticleType.FIREFLIES -> 1
            config.particleType == ParticleType.CHERRY_PETALS -> 2
            else -> 0 // FLOATING_DUST
        }
        setUniform1i(particleProgram, "uParticleType", particleCode)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    private fun drawFallbackScreen() {
        if (defaultProgram == 0) return
        GLES20.glUseProgram(defaultProgram)
        val posHandle = GLES20.glGetAttribLocation(defaultProgram, "aPosition")
        val texHandle = GLES20.glGetAttribLocation(defaultProgram, "aTexCoord")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        setUniform1f(defaultProgram, "uTime", 0f)
        setUniform1f(defaultProgram, "uLayerDepth", 0.5f)
        setUniform2f(defaultProgram, "uTilt", 0f, 0f)
        setUniform1f(defaultProgram, "uParallaxScale", 0f)
        setUniform1f(defaultProgram, "uHomeScroll", 0f)
        setUniform1f(defaultProgram, "uAudioEnergy", 0f)
        setUniform1f(defaultProgram, "uUnlockProgress", 1f)
        setUniform2f(defaultProgram, "uRippleCenter", 0.5f, 0.5f)
        setUniform1f(defaultProgram, "uRippleAge", 999f)
        setUniform1f(defaultProgram, "uRippleIntensity", 0f)
        setUniform1i(defaultProgram, "uAmoledMode", 0)
        setUniform1f(defaultProgram, "uAmoledThreshold", 0.05f)
        setUniform1i(defaultProgram, "uBatteryCooler", 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    private fun buildShaders() {
        // Vertex Shader common to all layer quads
        val vertexShaderCode = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            
            uniform float uLayerDepth;
            uniform vec2 uTilt;
            uniform float uParallaxScale;
            uniform float uHomeScroll;
            uniform float uTime;
            uniform float uSwaySpeed;
            uniform float uSwayAmp;
            uniform float uAudioEnergy;
            uniform float uUnlockProgress;
            
            varying vec2 vTexCoord;
            
            void main() {
                vTexCoord = aTexCoord;
                vec2 pos = aPosition;
                
                // Depth-proportional parallax translation
                float depthFactor = (uLayerDepth - 0.5);
                pos += uTilt * depthFactor * uParallaxScale;
                pos.x -= uHomeScroll * depthFactor * 0.12;
                
                // Wind sway on upper portion of vertical layers (Foreground)
                if (uSwayAmp > 0.0001) {
                    float swayHeight = clamp(1.0 - aTexCoord.y, 0.0, 1.0);
                    float sway = sin(uTime * uSwaySpeed + pos.x * 2.0) * uSwayAmp * swayHeight * (1.0 + uAudioEnergy * 0.8);
                    pos.x += sway;
                }
                
                // Unlock scatter / outward expansion
                if (uUnlockProgress < 0.999) {
                    vec2 centerDir = normalize(pos);
                    pos += centerDir * (1.0 - uUnlockProgress) * 0.25 * depthFactor;
                }
                
                gl_Position = vec4(pos, 0.0, 1.0);
            }
        """.trimIndent()

        // Common fragment header with touch ripple & power saving
        val commonFragmentFunctions = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform float uTime;
            uniform float uLayerDepth;
            uniform vec2 uRippleCenter;
            uniform float uRippleAge;
            uniform float uRippleIntensity;
            uniform int uAmoledMode;
            uniform float uAmoledThreshold;
            uniform int uBatteryCooler;
            uniform float uAudioEnergy;

            vec2 applyTouchRipple(vec2 uv) {
                if (uRippleAge < 2.0 && uRippleIntensity > 0.0) {
                    vec2 diff = uv - uRippleCenter;
                    float dist = length(diff);
                    if (dist > 0.001) {
                        float wave = sin(dist * 38.0 - uRippleAge * 10.0) * exp(-dist * 4.5) * max(0.0, 1.0 - uRippleAge / 2.0);
                        uv += normalize(diff) * wave * 0.025 * uRippleIntensity * (uLayerDepth + 0.2);
                    }
                }
                return uv;
            }

            vec4 applyPostProcessing(vec4 color) {
                // Audio pulse glow
                if (uAudioEnergy > 0.05) {
                    color.rgb += vec3(uAudioEnergy * 0.12 * uLayerDepth);
                }
                // Battery Cooler Mode (shifts cooler/teal, suppresses red for power)
                if (uBatteryCooler == 1) {
                    color.rgb = mix(color.rgb, vec3(color.b * 0.75, color.g * 0.95, color.b * 1.15), 0.35);
                }
                // AMOLED True Black cutoff
                if (uAmoledMode == 1) {
                    float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    if (lum < uAmoledThreshold) {
                        color = vec4(0.0, 0.0, 0.0, color.a);
                    }
                }
                return color;
            }
        """.trimIndent()

        // 1. SKY Fragment Shader
        val skyFragmentCode = """
            $commonFragmentFunctions
            uniform float uCloudSpeed;
            uniform float uSunAngle;
            uniform float uSkyLightShift;

            void main() {
                vec2 uv = applyTouchRipple(vTexCoord);
                
                // Continuous slow cloud drift UV shift
                uv.x = fract(uv.x + uTime * uCloudSpeed * 0.3);

                vec4 color = texture2D(uTexture, uv);
                if (color.a < 0.01) discard;

                // Celestial lighting shift (solar angle)
                if (uSunAngle != 0.0) {
                    float sunElevation = sin(uSunAngle);
                    if (sunElevation > 0.0) {
                        // Warm golden sun tint during daylight
                        vec3 goldenWarmth = vec3(1.15, 1.05, 0.88);
                        color.rgb = mix(color.rgb, color.rgb * goldenWarmth, 0.25 * sunElevation);
                    } else {
                        // Twilight / Night coolness
                        vec3 nightTone = vec3(0.65, 0.75, 1.05);
                        color.rgb = mix(color.rgb, color.rgb * nightTone, 0.35 * abs(sunElevation));
                    }
                }

                // Ambient light shift modulation
                if (uSkyLightShift > 0.0) {
                    float pulse = sin(uTime * 0.4) * 0.5 + 0.5;
                    color.rgb += vec3(pulse * uSkyLightShift * 0.15);
                }

                gl_FragColor = applyPostProcessing(color);
            }
        """.trimIndent()

        // 2. WATER Fragment Shader (Sine wave ripple & caustics)
        val waterFragmentCode = """
            $commonFragmentFunctions
            uniform float uWaterSpeed;
            uniform float uWaterAmp;
            uniform float uWaveFreq;
            uniform float uCaustic;

            void main() {
                vec2 uv = applyTouchRipple(vTexCoord);

                // Multi-frequency wave displacement
                float wave1 = sin(uv.y * uWaveFreq + uTime * uWaterSpeed);
                float wave2 = cos(uv.x * (uWaveFreq * 0.8) - uTime * (uWaterSpeed * 1.3));
                vec2 waveDisp = vec2(wave2, wave1) * uWaterAmp * (1.0 + uAudioEnergy * 0.8);
                uv += waveDisp;

                vec4 color = texture2D(uTexture, uv);
                if (color.a < 0.01) discard;

                // Specular caustic reflection shimmer
                if (uCaustic > 0.0) {
                    float caustic = pow(sin(uv.x * 35.0 + uTime * 2.2) * cos(uv.y * 30.0 - uTime * 1.7), 2.0) * uCaustic;
                    color.rgb += vec3(caustic * 0.25, caustic * 0.35, caustic * 0.4);
                }

                gl_FragColor = applyPostProcessing(color);
            }
        """.trimIndent()

        // 3. FOREGROUND Fragment Shader (Sway and high detail micro-motion)
        val foregroundFragmentCode = """
            $commonFragmentFunctions
            uniform float uSwaySpeed;
            uniform float uSwayAmp;

            void main() {
                vec2 uv = applyTouchRipple(vTexCoord);
                vec4 color = texture2D(uTexture, uv);
                if (color.a < 0.01) discard;

                gl_FragColor = applyPostProcessing(color);
            }
        """.trimIndent()

        // 4. DEFAULT Fragment Shader (Ground & Background)
        val defaultFragmentCode = """
            $commonFragmentFunctions

            void main() {
                vec2 uv = applyTouchRipple(vTexCoord);
                vec4 color = texture2D(uTexture, uv);
                if (color.a < 0.01) discard;

                gl_FragColor = applyPostProcessing(color);
            }
        """.trimIndent()

        // 5. PARTICLE Fragment Shader (Rain streaks, fireflies, dust, snow)
        val particleFragmentCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform float uTime;
            uniform float uUnlockProgress;
            uniform float uAudioEnergy;
            uniform int uParticleType;

            // Pseudo-random hash
            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
            }

            void main() {
                vec2 uv = vTexCoord;
                vec4 color = vec4(0.0);

                if (uParticleType == 3) {
                    // Rain Streaks: fast vertical lines
                    float slant = uv.x * 20.0 + uv.y * 3.0;
                    float speed = uTime * 18.0;
                    float rainLine = sin(slant + speed);
                    float rainDrop = step(0.96, rainLine) * step(0.85, hash(floor(vec2(slant, speed * 0.2))));
                    color = vec4(0.7, 0.85, 1.0, rainDrop * 0.45);
                } else if (uParticleType == 4) {
                    // Snow Flakes: gentle drifting circles
                    vec2 p = uv * vec2(15.0, 30.0);
                    p.y += uTime * 2.5;
                    p.x += sin(p.y * 0.5 + uTime) * 0.6;
                    vec2 cell = fract(p) - 0.5;
                    float d = length(cell);
                    float flake = smoothstep(0.2, 0.05, d) * step(0.7, hash(floor(p)));
                    color = vec4(0.95, 0.98, 1.0, flake * 0.65);
                } else if (uParticleType == 1) {
                    // Glowing Fireflies: floating pulsating spheres
                    vec2 p = uv * vec2(10.0, 20.0);
                    p.x += sin(uTime * 1.2 + p.y) * 1.5;
                    p.y += cos(uTime * 0.9 + p.x) * 1.2;
                    vec2 cell = fract(p) - 0.5;
                    float d = length(cell);
                    float pulse = sin(uTime * 3.0 + hash(floor(p)) * 6.28) * 0.5 + 0.5;
                    float glow = smoothstep(0.35, 0.02, d) * pulse * step(0.65, hash(floor(p)));
                    color = vec4(0.4, 1.0, 0.5, glow * (0.7 + uAudioEnergy * 0.5));
                } else if (uParticleType == 2) {
                    // Cherry Blossom Petals: drifting pink flakes
                    vec2 p = uv * vec2(12.0, 24.0);
                    p.y += uTime * 2.0;
                    p.x += cos(p.y * 0.4 + uTime * 1.2) * 1.0;
                    vec2 cell = fract(p) - 0.5;
                    float d = length(cell);
                    float petal = smoothstep(0.25, 0.05, d) * step(0.72, hash(floor(p)));
                    color = vec4(1.0, 0.65, 0.8, petal * 0.55);
                } else if (uParticleType == 0) {
                    // Floating Luminous Dust
                    vec2 p = uv * vec2(14.0, 28.0);
                    p.y += sin(uTime * 0.4 + p.x) * 0.8;
                    p.x += cos(uTime * 0.3 + p.y) * 0.8;
                    vec2 cell = fract(p) - 0.5;
                    float d = length(cell);
                    float dust = smoothstep(0.15, 0.02, d) * step(0.8, hash(floor(p)));
                    color = vec4(0.9, 0.95, 1.0, dust * 0.4);
                }

                if (color.a < 0.02) discard;
                gl_FragColor = color;
            }
        """.trimIndent()

        skyProgram = GlShaderUtils.createProgram(vertexShaderCode, skyFragmentCode)
        waterProgram = GlShaderUtils.createProgram(vertexShaderCode, waterFragmentCode)
        foregroundProgram = GlShaderUtils.createProgram(vertexShaderCode, foregroundFragmentCode)
        defaultProgram = GlShaderUtils.createProgram(vertexShaderCode, defaultFragmentCode)
        particleProgram = GlShaderUtils.createProgram(vertexShaderCode, particleFragmentCode)
    }

    private fun loadSceneTextures() {
        val scene = currentScene ?: return

        // Delete previous textures
        for (i in layerTextures.indices) {
            if (layerTextures[i] != 0) {
                GLES20.glDeleteTextures(1, layerTextures, i)
                layerTextures[i] = 0
            }
        }

        // Upload each layer's bitmap into a dedicated GL texture
        for (layer in scene.layers) {
            val idx = layer.type.ordinal
            if (idx in layerTextures.indices) {
                layerTextures[idx] = GlShaderUtils.loadTexture(layer.bitmap)
            }
        }
    }

    private fun setUniform1f(program: Int, name: String, v: Float) {
        val loc = GLES20.glGetUniformLocation(program, name)
        if (loc >= 0) GLES20.glUniform1f(loc, v)
    }

    private fun setUniform2f(program: Int, name: String, x: Float, y: Float) {
        val loc = GLES20.glGetUniformLocation(program, name)
        if (loc >= 0) GLES20.glUniform2f(loc, x, y)
    }

    private fun setUniform1i(program: Int, name: String, v: Int) {
        val loc = GLES20.glGetUniformLocation(program, name)
        if (loc >= 0) GLES20.glUniform1i(loc, v)
    }
}
