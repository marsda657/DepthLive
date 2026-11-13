package com.example.segmentation

import com.example.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LandscapeMaskTest {
    @Test
    fun mapsAde20kIndicesAndLeavesOthersUnassigned() {
        val mask = LandscapeMask(byteArrayOf(3, 22, 14, 1), 2, 2)
        assertEquals(LayerType.SKY, mask.layerAt(0, 0, 2, 2))
        assertEquals(LayerType.WATER, mask.layerAt(1, 0, 2, 2))
        assertEquals(LayerType.GROUND, mask.layerAt(0, 1, 2, 2))
        assertNull(mask.layerAt(1, 1, 2, 2)) // wall
        assertEquals(LayerType.SKY, mask.layerAt(0, 0, 8, 8))
    }
}
