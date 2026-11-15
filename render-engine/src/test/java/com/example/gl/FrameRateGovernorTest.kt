package com.example.gl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateGovernorTest {
    private val governor = FrameRateGovernor()
    private var nowNs = 1L

    private fun frames(intervalNs: Long, count: Int) {
        repeat(count) {
            nowNs += intervalNs
            governor.onFrame(nowNs)
        }
    }

    @Test
    fun persistentSlowFramesStepDownAndStableFramesProbeRecovery() {
        governor.onFrame(nowNs)
        frames(50_000_000L, 61)
        assertEquals(30, governor.targetFps)
        frames(100_000_000L, 31)
        assertEquals(15, governor.targetFps)

        frames(66_666_667L, 230)
        assertEquals(30, governor.targetFps)
        frames(33_333_334L, 460)
        assertEquals(60, governor.targetFps)
    }

    @Test
    fun fastFramesAndShortStallsDoNotReduceFrameRate() {
        governor.onFrame(nowNs)
        frames(16_666_667L, 100)
        frames(100_000_000L, 1)
        frames(16_666_667L, 100)
        assertEquals(60, governor.targetFps)
        assertTrue(governor.measuredFps > 50f)
    }

    @Test
    fun unsuccessfulRecoveryProbeDropsBackDown() {
        governor.onFrame(nowNs)
        frames(50_000_000L, 61)
        assertEquals(30, governor.targetFps)
        frames(33_333_334L, 460)
        assertEquals(60, governor.targetFps)
        frames(50_000_000L, 61)
        assertEquals(30, governor.targetFps)
    }

    @Test
    fun batteryAndUserLimitsAreNeverExceeded() {
        governor.configure(60, true)
        assertEquals(30, governor.targetFps)
        governor.onFrame(nowNs)
        frames(33_333_334L, 600)
        assertEquals(30, governor.targetFps)

        governor.configure(15, false)
        assertEquals(15, governor.targetFps)
        governor.onFrame(nowNs)
        frames(66_666_667L, 300)
        assertEquals(15, governor.targetFps)
        governor.configure(60, false)
        assertEquals(60, governor.targetFps)
    }

    @Test
    fun longPauseDoesNotCountAsPoorPerformance() {
        governor.onFrame(nowNs)
        frames(16_666_667L, 120)
        nowNs += 10_000_000_000L
        assertEquals(0L, governor.delayBeforeFrameNs(nowNs))
        governor.onFrame(nowNs)
        frames(16_666_667L, 200)
        assertEquals(60, governor.targetFps)
    }

    @Test
    fun pacingAccountsForTimeAlreadySpentSinceLastFrame() {
        governor.configure(30, false)
        governor.onFrame(nowNs)
        assertEquals(22_333_333L, governor.delayBeforeFrameNs(nowNs + 10_000_000L))
        assertEquals(0L, governor.delayBeforeFrameNs(nowNs + 40_000_000L))
    }
}
