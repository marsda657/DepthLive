package com.example.segmentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundMaskTest {
    @Test
    fun scalesModelMaskAndRecognizesOnlyKnownObjectClasses() {
        val mask = ForegroundMask(byteArrayOf(0, 15, 20, 255.toByte()), 2, 2)
        assertFalse(mask.contains(0, 0, 4, 4))
        assertTrue(mask.contains(3, 0, 4, 4))
        assertTrue(mask.contains(0, 3, 4, 4))
        assertFalse(mask.contains(3, 3, 4, 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidMaskDimensions() {
        ForegroundMask(byteArrayOf(0), 2, 2)
    }
}
