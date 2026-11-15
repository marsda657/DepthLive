package com.example.segmentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPipeBitmapOwnershipTest {
    @Test
    fun closingMediaPipeImagesKeepsSourceBitmapUsable() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        try {
            repeat(2) {
                ownedMediaPipeImage(bitmap).close()
                assertFalse("Closing the MediaPipe image recycled its input", bitmap.isRecycled)
                assertEquals(Color.RED, bitmap.getPixel(0, 0))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun sequentialSegmentationKeepsSourceBitmapUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = context.assets.open("sample_landscape.jpg").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }
        try {
            val foreground = MediaPipeForegroundSegmenter.segment(context, bitmap)
            assertTrue(foreground.pixels.isNotEmpty())
            assertFalse("Foreground segmentation recycled its input", bitmap.isRecycled)

            val landscape = MediaPipeLandscapeSegmenter.segment(context, bitmap)
            assertEquals(landscape.width * landscape.height, landscape.pixels.size)
            assertTrue(landscape.pixels.isNotEmpty())
            assertFalse("Landscape segmentation recycled its input", bitmap.isRecycled)
            bitmap.getPixel(0, 0)
        } finally {
            bitmap.recycle()
        }
    }
}
