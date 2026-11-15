package com.example.gl

import com.example.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WaterHitTestTest {
    private val water = HitLayer(LayerType.WATER, 0.4f)
    private val person = HitLayer(LayerType.FOREGROUND, 0.9f)
    private val noCrop = TexturePoint(1f, 1f)

    @Test
    fun rippleStartsOnlyOnVisibleWater() {
        val layers = listOf(water, person)
        val opaque: (HitLayer, TexturePoint) -> Boolean = { layer, point ->
            if (layer.type == LayerType.WATER) point.y > 0.5f
            else point.x > 0.4f && point.x < 0.6f
        }
        fun hit(x: Float, y: Float) = visibleWaterAt(x, y, layers, 0f, 0f, 0f, 0f, noCrop, opaque)
        assertNotNull(hit(0.2f, 0.8f))
        assertNull(hit(0.5f, 0.8f))
        assertNull(hit(0.2f, 0.2f))
        assertNull(hit(-0.1f, 0.8f))
        assertNull(visibleWaterAt(0.2f, 0.8f, emptyList(), 0f, 0f, 0f, 0f, noCrop, opaque))
    }

    @Test
    fun touchCoordinatesFollowLayerParallax() {
        val point = visibleWaterAt(0.5f, 0.5f, listOf(water), 1f, -1f, 0.1f, 0.5f, noCrop) { _, _ -> true }
        requireNotNull(point)
        assertEquals(0.502f, point.x, 0.00001f)
        assertEquals(0.505f, point.y, 0.00001f)
    }

    @Test
    fun subjectsOccludeWaterEvenWhenDepthEstimatesOverlap() {
        val foreground = HitLayer(LayerType.FOREGROUND, 0.1f)
        assertNull(visibleWaterAt(0.5f, 0.5f, listOf(water, foreground), 0f, 0f, 0f, 0f, noCrop) { _, _ -> true })
    }

    @Test
    fun centerCropPreservesAspectAndMapsTapToVisibleImage() {
        val crop = centerCropScale(2000, 1000, 1000, 2000)
        assertEquals(0.25f, crop.x, 0.00001f)
        assertEquals(1f, crop.y, 0.00001f)
        val point = visibleWaterAt(0f, 0.5f, listOf(water), 0f, 0f, 0f, 0f, crop) { _, _ -> true }
        requireNotNull(point)
        assertEquals(0.375f, point.x, 0.00001f)
        val tallCrop = centerCropScale(1000, 2000, 2000, 1000)
        assertEquals(0.25f, tallCrop.y, 0.00001f)
    }
}
