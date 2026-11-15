package com.example.segmentation

import com.example.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticClassificationTest {
    @Test
    fun routesDetectedSceneryAndObjectsWithoutGuessingUnknownRegions() {
        assertEquals(LayerType.WATER, layerForPixel(0, 129))
        assertEquals(LayerType.WATER, layerForPixel(0, 110))
        assertEquals(LayerType.SKY, layerForPixel(0, 3))
        assertEquals(LayerType.FOREGROUND, layerForPixel(15, 22))
        assertEquals(LayerType.FOREGROUND, layerForPixel(0, 21))
        assertEquals(LayerType.FOREGROUND, layerForPixel(0, 127))
        assertEquals(LayerType.BACKGROUND, layerForPixel(0, 0))
        assertEquals(LayerType.BACKGROUND, layerForPixel(0, 1))
    }

    @Test
    fun onlyVegetationWithoutConflictingObjectDetectionSways() {
        assertTrue(isSwayablePixel(0, 5))
        assertTrue(isSwayablePixel(16, 18))
        assertFalse(isSwayablePixel(15, 5))
        assertFalse(isSwayablePixel(7, 5))
        assertFalse(isSwayablePixel(0, 13))
        assertFalse(isSwayablePixel(0, 0))
    }
}
