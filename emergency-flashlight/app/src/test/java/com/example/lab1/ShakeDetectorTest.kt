package com.example.lab1

import com.example.lab1.helpers.ShakeDetector
import org.junit.Assert.*
import org.junit.Test

class ShakeDetectorTest {
    @Test fun restAndStaticTiltsDoNotTrigger() {
        val detector = ShakeDetector()
        val g = ShakeDetector.GRAVITY
        listOf(floatArrayOf(g, 0f, 0f), floatArrayOf(0f, -g, 0f), floatArrayOf(0f, 0f, g),
            floatArrayOf(g / 1.41421356f, g / 1.41421356f, 0f)).forEachIndexed { i, v ->
            val result = detector.accept(v[0], v[1], v[2], i * 2_000_000_000L)
            assertFalse(result.triggered)
            assertEquals(1f, result.magnitudeG, 0.001f)
        }
    }

    @Test fun impulseTriggersOnceUntilRest() {
        val detector = ShakeDetector()
        assertTrue(detector.accept(30f, 0f, 0f, 0).triggered)
        assertFalse(detector.accept(30f, 0f, 0f, 2_000_000_000L).triggered)
        assertFalse(detector.accept(0f, 0f, ShakeDetector.GRAVITY, 2_100_000_000L).triggered)
        assertTrue(detector.accept(0f, -30f, 0f, 2_200_000_000L).triggered)
    }

    @Test fun cooldownRejectsMultiplePeaksOfOneShake() {
        val detector = ShakeDetector()
        assertTrue(detector.accept(30f, 0f, 0f, 0).triggered)
        detector.accept(0f, 0f, ShakeDetector.GRAVITY, 100_000_000L)
        assertFalse(detector.accept(30f, 0f, 0f, 900_000_000L).triggered)
        assertTrue(detector.accept(30f, 0f, 0f, 1_000_000_000L).triggered)
    }

    @Test fun thresholdIncludesBoundaryAndCanBeAdjusted() {
        val detector = ShakeDetector(2f)
        assertFalse(detector.accept(1.99f * ShakeDetector.GRAVITY, 0f, 0f, 0).triggered)
        assertTrue(detector.accept(2f * ShakeDetector.GRAVITY, 0f, 0f, 1).triggered)
        detector.thresholdG = 3.5f
        assertFalse(detector.accept(30f, 0f, 0f, 2).triggered)
        assertTrue(detector.accept(40f, 0f, 0f, 3).triggered)
    }

    @Test fun pauseResetAllowsFreshDetection() {
        val detector = ShakeDetector()
        assertTrue(detector.accept(30f, 0f, 0f, 0).triggered)
        detector.reset()
        assertTrue(detector.accept(30f, 0f, 0f, 1).triggered)
    }

    @Test fun invalidSamplesAreIgnored() {
        val detector = ShakeDetector()
        assertFalse(detector.accept(Float.NaN, 0f, 0f, 0).triggered)
        assertFalse(detector.accept(0f, Float.POSITIVE_INFINITY, 0f, 0).triggered)
        assertFalse(detector.accept(30f, 0f, 0f, -1).triggered)
        assertTrue(detector.accept(30f, 0f, 0f, 0).triggered)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectThresholdBelowGravity() { ShakeDetector(0.5f) }
}
