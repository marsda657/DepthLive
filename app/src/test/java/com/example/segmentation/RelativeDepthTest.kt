package com.example.segmentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RelativeDepthTest {
    @Test
    fun normalizesInverseDepthAndResizesWithoutChangingDirection() {
        val depth = RelativeDepth.normalize(floatArrayOf(10f, 20f, 30f, 50f), 2, 2)
        assertArrayEquals(
            floatArrayOf(0f, 0f, .25f, .25f, .5f, .5f, 1f, 1f),
            depth.resize(4, 2),
            .00001f
        )
    }

    @Test
    fun rejectsInvalidAndFlatOutputs() {
        assertThrows(IllegalArgumentException::class.java) {
            RelativeDepth.normalize(floatArrayOf(2f, 2f), 2, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelativeDepth.normalize(floatArrayOf(Float.NaN, 1f), 2, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelativeDepth.normalize(floatArrayOf(1f), 2, 1)
        }
    }
}
