package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.example.model.ParticleType
import com.example.model.ProcessedScene
import com.example.model.ShaderConfig
import com.example.segmentation.ImageSegmentationEngine
import com.example.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Central repository managing active scene bitmaps, layer configurations, and persisted settings.
 */
object WallpaperRepository {
    private const val TAG = "WallpaperRepository"
    private const val PREFS_NAME = "depthlive_wallpaper_prefs"
    private var prefs: SharedPreferences? = null
    private val sceneLock = Any()
    private val sceneMutex = Mutex()
    private var sceneGeneration = 0L

    private val _activeScene = MutableStateFlow<ProcessedScene?>(null)
    val activeScene: StateFlow<ProcessedScene?> = _activeScene.asStateFlow()

    private val _shaderConfig = MutableStateFlow(ShaderConfig())
    val shaderConfig: StateFlow<ShaderConfig> = _shaderConfig.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val configListeners = mutableListOf<(ShaderConfig) -> Unit>()

    fun init(context: Context) {
        WeatherRepository.init(context)
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadConfigFromPrefs()

            // Recover the user's last photo after process/service recreation; use the sample only
            // for first launch or if the saved photo is unavailable or corrupt.
            if (_activeScene.value == null) {
                val generation = beginSceneLoad()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        sceneMutex.withLock {
                            if (isCurrent(generation)) loadSavedOrBundledScene(context.applicationContext, generation)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not load the initial scene", e)
                    } finally {
                        finishSceneLoad(generation)
                    }
                }
            }
        }
    }

    fun addConfigListener(listener: (ShaderConfig) -> Unit) {
        configListeners.add(listener)
        listener(_shaderConfig.value)
    }

    fun removeConfigListener(listener: (ShaderConfig) -> Unit) {
        configListeners.remove(listener)
    }

    fun updateConfig(newConfig: ShaderConfig) {
        _shaderConfig.value = newConfig
        saveConfigToPrefs(newConfig)
        configListeners.forEach { it(newConfig) }
    }

    private fun beginSceneLoad(): Long = synchronized(sceneLock) {
        _isProcessing.value = true
        ++sceneGeneration
    }

    private fun isCurrent(generation: Long): Boolean = synchronized(sceneLock) {
        generation == sceneGeneration
    }

    private fun publishScene(generation: Long, scene: ProcessedScene) = synchronized(sceneLock) {
        if (generation == sceneGeneration) _activeScene.value = scene
    }

    private fun finishSceneLoad(generation: Long) = synchronized(sceneLock) {
        if (generation == sceneGeneration) _isProcessing.value = false
    }

    private suspend fun loadSavedOrBundledScene(context: Context, generation: Long) {
        val store = PhotoStore(context)
        val bitmap = if (store.photoFile.isFile) {
            try {
                store.decode(store.photoFile)
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode saved photo; loading the sample instead: ${e.message}")
                null
            }
        } else {
            null
        }

        if (bitmap != null) {
            try {
                val scene = ImageSegmentationEngine.processImage(context, bitmap, "My Photo")
                publishScene(generation, scene)
                Log.d(TAG, "Restored saved photo scene")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not process saved photo; loading the sample instead", e)
            }
        }

        if (isCurrent(generation)) loadBundledSample(context, generation)
    }

    suspend fun loadBundledSampleScene(context: Context) {
        val generation = beginSceneLoad()
        try {
            withContext(Dispatchers.IO) {
                sceneMutex.withLock {
                    if (isCurrent(generation)) loadBundledSample(context, generation)
                }
            }
        } finally {
            finishSceneLoad(generation)
        }
    }

    private suspend fun loadBundledSample(context: Context, generation: Long) {
            val options = BitmapFactory.Options().apply {
                inScaled = false // Do NOT auto-scale based on emulator density
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            // 1. Try reading directly from assets folder
            var bitmap: Bitmap? = try {
                context.assets.open("sample_landscape.jpg").use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not open asset sample_landscape.jpg: ${e.message}")
                null
            }

            // 2. If assets not accessible, try compiled drawable resource ID
            if (bitmap == null) {
                bitmap = try {
                    BitmapFactory.decodeResource(context.resources, com.example.R.drawable.sample_landscape, options)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not decode R.drawable.sample_landscape: ${e.message}")
                    null
                }
            }

            // 3. Fallback to sample_wallpaper_1794295463626
            if (bitmap == null) {
                bitmap = try {
                    BitmapFactory.decodeResource(context.resources, com.example.R.drawable.sample_wallpaper_1794295463626, options)
                } catch (e: Exception) {
                    null
                }
            }

            // 4. Guaranteed procedural fallback if files were somehow unreadable
            if (bitmap == null) {
                bitmap = createProceduralScenicLandscape()
            }

            Log.d(TAG, "Loaded base image: ${bitmap.width}x${bitmap.height}, starting segmentation...")
            val scene = ImageSegmentationEngine.processImage(context, bitmap, "Alpine Lake & Peaks")
            publishScene(generation, scene)
            Log.d(TAG, "Scene segmentation completed with ${scene.layers.size} layers")
    }

    suspend fun setCustomPhoto(context: Context, uri: Uri, name: String = "Custom Photo") {
        val generation = beginSceneLoad()
        try {
            withContext(Dispatchers.IO) {
                sceneMutex.withLock {
                    check(isCurrent(generation)) { "Photo selection was superseded" }
                    val bitmap = PhotoStore(context.applicationContext).copyFrom(uri)
                    val scene = ImageSegmentationEngine.processImage(context, bitmap, name)
                    publishScene(generation, scene)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error processing custom photo: ${e.message}", e)
            throw e
        } finally {
            finishSceneLoad(generation)
        }
    }

    /**
     * Fallback scenic landscape generator ensuring there is never a blank screen.
     */
    private fun createProceduralScenicLandscape(): Bitmap {
        val w = 540
        val h = 960
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = true }

        // Sky gradient
        paint.color = Color.rgb(79, 195, 247)
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)

        // Distant mountains
        paint.color = Color.rgb(121, 134, 203)
        val path = android.graphics.Path().apply {
            moveTo(0f, h * 0.42f)
            lineTo(w * 0.3f, h * 0.28f)
            lineTo(w * 0.6f, h * 0.35f)
            lineTo(w * 0.85f, h * 0.25f)
            lineTo(w.toFloat(), h * 0.42f)
            lineTo(w.toFloat(), h * 0.52f)
            lineTo(0f, h * 0.52f)
            close()
        }
        canvas.drawPath(path, paint)

        // Lake Water
        paint.color = Color.rgb(41, 182, 246)
        canvas.drawRect(0f, h * 0.48f, w.toFloat(), h * 0.75f, paint)

        // Ground shoreline
        paint.color = Color.rgb(129, 199, 132)
        canvas.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), paint)

        // Foreground Tree silhouette
        paint.color = Color.rgb(46, 125, 50)
        canvas.drawRect(w * 0.7f, h * 0.35f, w * 0.85f, h * 0.95f, paint)

        return bmp
    }

    private fun loadConfigFromPrefs() {
        val p = prefs ?: return
        val config = ShaderConfig(
            cloudDriftSpeed = p.getFloat("cloudDriftSpeed", 0.035f),
            celestialSunTracking = p.getBoolean("celestialSunTracking", true),
            skyLightShift = p.getFloat("skyLightShift", 0.2f),
            waterRippleSpeed = p.getFloat("waterRippleSpeed", 1.3f),
            waterRippleAmplitude = p.getFloat("waterRippleAmplitude", 0.016f),
            waterWaveFrequency = p.getFloat("waterWaveFrequency", 24.0f),
            causticShimmer = p.getFloat("causticShimmer", 0.35f),
            foregroundSwaySpeed = p.getFloat("foregroundSwaySpeed", 1.4f),
            foregroundSwayAmplitude = p.getFloat("foregroundSwayAmplitude", 0.012f),
            parallaxDegrees = p.getFloat("parallaxDegrees", 4.0f),
            gyroscopeSensitivity = p.getFloat("gyroscopeSensitivity", 1.0f),
            homeScreenScrollParallax = p.getBoolean("homeScreenScrollParallax", true),
            touchRippleIntensity = p.getFloat("touchRippleIntensity", 1.0f),
            particleType = try {
                ParticleType.valueOf(p.getString("particleType", ParticleType.FLOATING_DUST.name) ?: ParticleType.FLOATING_DUST.name)
            } catch (e: Exception) {
                ParticleType.FLOATING_DUST
            },
            amoledMode = p.getBoolean("amoledMode", false),
            amoledThreshold = p.getFloat("amoledThreshold", 0.05f),
            batterySaverCoolPalette = p.getBoolean("batterySaverCoolPalette", true),
            targetFps = p.getInt("targetFps", 60),
            weatherReactivity = p.getBoolean("weatherReactivity", true),
            audioReactive = p.getBoolean("audioReactive", false),
            audioSensitivity = p.getFloat("audioSensitivity", 1.0f)
        )
        _shaderConfig.value = config
    }

    private fun saveConfigToPrefs(c: ShaderConfig) {
        prefs?.edit()?.apply {
            putFloat("cloudDriftSpeed", c.cloudDriftSpeed)
            putBoolean("celestialSunTracking", c.celestialSunTracking)
            putFloat("skyLightShift", c.skyLightShift)
            putFloat("waterRippleSpeed", c.waterRippleSpeed)
            putFloat("waterRippleAmplitude", c.waterRippleAmplitude)
            putFloat("waterWaveFrequency", c.waterWaveFrequency)
            putFloat("causticShimmer", c.causticShimmer)
            putFloat("foregroundSwaySpeed", c.foregroundSwaySpeed)
            putFloat("foregroundSwayAmplitude", c.foregroundSwayAmplitude)
            putFloat("parallaxDegrees", c.parallaxDegrees)
            putFloat("gyroscopeSensitivity", c.gyroscopeSensitivity)
            putBoolean("homeScreenScrollParallax", c.homeScreenScrollParallax)
            putFloat("touchRippleIntensity", c.touchRippleIntensity)
            putString("particleType", c.particleType.name)
            putBoolean("amoledMode", c.amoledMode)
            putFloat("amoledThreshold", c.amoledThreshold)
            putBoolean("batterySaverCoolPalette", c.batterySaverCoolPalette)
            putInt("targetFps", c.targetFps)
            putBoolean("weatherReactivity", c.weatherReactivity)
            putBoolean("audioReactive", c.audioReactive)
            putFloat("audioSensitivity", c.audioSensitivity)
            apply()
        }
    }
}
