package com.example

import android.graphics.Bitmap
import com.example.llm.MotionPromptManager
import com.example.model.LayerType
import com.example.model.ParticleType
import com.example.model.PresetsData
import com.example.model.ShaderConfig
import com.example.segmentation.ImageSegmentationEngine
import com.example.weather.WeatherClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DepthLiveUnitTest {

    @Test
    fun presetsData_has12PresetsWithValidRanges() {
        val presets = PresetsData.presets
        assertEquals(12, presets.size)

        for (preset in presets) {
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.description.isNotBlank())
            assertTrue(preset.config.cloudDriftSpeed in 0.0f..0.2f)
            assertTrue(preset.config.waterRippleSpeed in 0.1f..5.0f)
            assertTrue(preset.config.parallaxDegrees in 0.5f..4.0f)
        }
    }

    @Test
    fun motionPromptManager_parsesRainKeyword() = runBlocking {
        val base = ShaderConfig()
        val result = MotionPromptManager.parseMotionPrompt("rainy downpour on water", base)
        assertEquals(ParticleType.RAIN_DROPS, result.config.particleType)
        assertTrue(result.config.waterRippleSpeed > base.waterRippleSpeed)
    }

    @Test
    fun motionPromptManager_parsesSnowKeyword() = runBlocking {
        val base = ShaderConfig()
        val result = MotionPromptManager.parseMotionPrompt("heavy winter snow", base)
        assertEquals(ParticleType.SNOW_FLAKES, result.config.particleType)
    }

    @Test
    fun motionPromptManager_parsesAmoledKeyword() = runBlocking {
        val base = ShaderConfig()
        val result = MotionPromptManager.parseMotionPrompt("amoled true black night", base)
        assertTrue(result.config.amoledMode)
    }

    @Test
    fun weatherClient_parsesRainAndSnowCodesCorrectly() {
        val rain = WeatherClient.parseWeatherCode(61, 15f)
        assertTrue(rain.isRaining)
        assertEquals(false, rain.isSnowing)

        val snow = WeatherClient.parseWeatherCode(71, -2f)
        assertTrue(snow.isSnowing)
        assertEquals(false, snow.isRaining)

        val clear = WeatherClient.parseWeatherCode(0, 22f)
        assertEquals(false, clear.isRaining)
        assertEquals(false, clear.isSnowing)
    }

    @Test
    fun imageSegmentationEngine_produces5LayersAndDepthMap() = runBlocking {
        val testBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val scene = ImageSegmentationEngine.processImage(testBmp, "Test Scene")

        assertNotNull(scene)
        assertEquals(5, scene.layers.size)
        assertNotNull(scene.depthMapBitmap)

        val layerTypes = scene.layers.map { it.type }
        assertTrue(layerTypes.contains(LayerType.SKY))
        assertTrue(layerTypes.contains(LayerType.BACKGROUND))
        assertTrue(layerTypes.contains(LayerType.WATER))
        assertTrue(layerTypes.contains(LayerType.GROUND))
        assertTrue(layerTypes.contains(LayerType.FOREGROUND))
    }
}
