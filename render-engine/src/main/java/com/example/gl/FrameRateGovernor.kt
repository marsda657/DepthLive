package com.example.gl

/**
 * Adapts the frame cap using frame-start intervals, which include buffer swaps
 * and pacing time, unlike CPU time spent inside onDrawFrame.
 */
internal class FrameRateGovernor {
    companion object {
        private const val SECOND_NS = 1_000_000_000L
        private const val WINDOW_NS = 3 * SECOND_NS
        private const val RECOVERY_NS = 15 * SECOND_NS
        private const val PAUSE_NS = SECOND_NS
    }

    var targetFps: Int = 60
        private set
    var measuredFps: Float = 0f
        private set

    private var maxFps = 60
    private var lastFrameNs = 0L
    private var windowStartNs = 0L
    private var framesInWindow = 0
    private var stableSinceNs = 0L

    fun configure(requestedFps: Int, lowBattery: Boolean) {
        val requestedCap = when {
            requestedFps >= 60 -> 60
            requestedFps >= 30 -> 30
            else -> 15
        }
        val newMax = if (lowBattery) minOf(requestedCap, 30) else requestedCap
        if (newMax != maxFps) {
            maxFps = newMax
            targetFps = newMax
            resetMeasurement()
        }
    }

    fun delayBeforeFrameNs(nowNs: Long): Long {
        if (lastFrameNs == 0L || nowNs - lastFrameNs >= PAUSE_NS) return 0L
        // A small margin avoids missing a display vsync due to scheduler jitter.
        return (SECOND_NS / targetFps - (nowNs - lastFrameNs) - 1_000_000L).coerceAtLeast(0L)
    }

    fun onFrame(nowNs: Long) {
        if (lastFrameNs == 0L || nowNs <= lastFrameNs || nowNs - lastFrameNs >= PAUSE_NS) {
            lastFrameNs = nowNs
            windowStartNs = nowNs
            framesInWindow = 0
            stableSinceNs = nowNs
            measuredFps = 0f
            return
        }

        lastFrameNs = nowNs
        framesInWindow++
        val elapsed = nowNs - windowStartNs
        if (elapsed < WINDOW_NS) return

        measuredFps = framesInWindow * SECOND_NS.toFloat() / elapsed
        when {
            measuredFps < targetFps * 0.85f && targetFps > 15 -> {
                targetFps = if (targetFps == 60) 30 else 15
                stableSinceNs = nowNs
            }
            measuredFps < targetFps * 0.9f -> stableSinceNs = nowNs
            targetFps < maxFps && nowNs - stableSinceNs >= RECOVERY_NS -> {
                targetFps = if (targetFps == 15) 30 else 60
                targetFps = minOf(targetFps, maxFps)
                stableSinceNs = nowNs
            }
        }
        windowStartNs = nowNs
        framesInWindow = 0
    }

    private fun resetMeasurement() {
        lastFrameNs = 0L
        windowStartNs = 0L
        framesInWindow = 0
        stableSinceNs = 0L
        measuredFps = 0f
    }
}
