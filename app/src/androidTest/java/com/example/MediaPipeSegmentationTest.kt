package com.example

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.segmentation.ImageSegmentationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPipeSegmentationTest {
    @Test
    fun bundledModelAnalyzesPhotoOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = context.assets.open("sample_landscape.jpg").use { BitmapFactory.decodeStream(it) }
        val scene = ImageSegmentationEngine.processImage(context, bitmap, "Test")
        assertTrue(scene.analysisMethod.startsWith("MediaPipe objects"))
        assertTrue(scene.analysisMethod.contains("ADE20K landscape"))
        assertTrue(scene.analysisMethod.contains("MiDaS relative depth"))
        assertEquals(5, scene.layers.size)
        assertEquals(scene.width * scene.height, scene.layers.sumOf { it.pixelCount })
        assertTrue(scene.layers.all { it.depth in 0f..1f })
        assertTrue(scene.layers.all { it.pixelCount > 0 })
        val depthPixels = IntArray(scene.width * scene.height)
        scene.depthMapBitmap.getPixels(depthPixels, 0, scene.width, 0, 0, scene.width, scene.height)
        assertTrue(depthPixels.toSet().size > 5)
    }
}
