package com.example.segmentation

import com.example.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LandscapeMaskTest {
    @Test
    fun mapsAde20kIndicesAndLeavesOthersUnassigned() {
        val mask = LandscapeMask(byteArrayOf(3, 22, 14, 1, 2, 5), 3, 2)
        assertEquals(LayerType.SKY, mask.layerAt(0, 0, 3, 2))
        assertEquals(LayerType.WATER, mask.layerAt(1, 0, 3, 2))
        assertEquals(LayerType.GROUND, mask.layerAt(2, 0, 3, 2))
        assertNull(mask.layerAt(0, 1, 3, 2)) // wall
        assertEquals(LayerType.BACKGROUND, mask.layerAt(1, 1, 3, 2)) // building
        assertEquals(LayerType.FOREGROUND, mask.layerAt(2, 1, 3, 2)) // tree
        assertEquals(LayerType.SKY, mask.layerAt(0, 0, 8, 6))
    }
}
