package com.example.lab1

import com.example.lab1.helpers.ml.Activity
import com.example.lab1.helpers.ml.AxisMap
import com.example.lab1.helpers.ml.ImuWindowBuilder
import com.example.lab1.helpers.ml.Prediction
import org.junit.Assert.*
import org.junit.Test

class ImuWindowBuilderTest {
    private val g = ImuWindowBuilder.GRAVITY
    private val tick = ImuWindowBuilder.TICK_NANOS

    /** Feeds [n] accelerometer events [stepNanos] apart starting at [startNanos]; returns the next timestamp. */
    private fun feed(b: ImuWindowBuilder, n: Int, x: Float, y: Float, z: Float, startNanos: Long = 0L, stepNanos: Long = tick): Long {
        var t = startNanos
        repeat(n) { b.onAccelerometer(x, y, z, t); t += stepNanos }
        return t
    }

    @Test fun windowIsReadyAfter128Rows() {
        val b = ImuWindowBuilder()
        feed(b, 127, 0f, 0f, g)
        assertNull(b.windowIfDue())
        feed(b, 1, 0f, 0f, g, startNanos = 127 * tick)
        assertEquals(128, b.fill)
        val w = b.windowIfDue()!!
        assertEquals(128 * 9, w.size)
    }

    @Test fun nextWindowComesEvery25Rows() {
        val b = ImuWindowBuilder()
        var t = feed(b, 128, 0f, 0f, g)
        assertNotNull(b.windowIfDue())
        t = feed(b, 24, 0f, 0f, g, t)
        assertNull(b.windowIfDue())
        feed(b, 1, 0f, 0f, g, t)
        assertNotNull(b.windowIfDue())
    }

    @Test fun channelOrderAndUnits() {
        val b = ImuWindowBuilder()
        b.onGyroscope(0.1f, 0.2f, 0.3f)
        feed(b, 128, 0f, 0f, g)
        val w = b.windowIfDue()!!
        val last = 127 * 9
        // body_acc is zero at rest, gyro is copied as rad/s, total_acc is in g
        for (i in 0..2) assertEquals(0f, w[last + i], 1e-4f)
        assertEquals(0.1f, w[last + 3], 1e-6f)
        assertEquals(0.2f, w[last + 4], 1e-6f)
        assertEquals(0.3f, w[last + 5], 1e-6f)
        assertEquals(0f, w[last + 6], 1e-4f)
        assertEquals(0f, w[last + 7], 1e-4f)
        assertEquals(1f, w[last + 8], 1e-4f)
    }

    @Test fun gravityIsRemovedAfterAStepChange() {
        val b = ImuWindowBuilder()
        val t = feed(b, 10, 0f, 0f, g)
        feed(b, 1000, g, 0f, 0f, t) // phone turned on its side, 20 s
        val w = b.windowIfDue()!!
        val last = 127 * 9
        assertEquals(0f, w[last], 1e-2f)
        assertEquals(1f, w[last + 6], 1e-4f)
    }

    @Test fun irregularRateIsResampledTo50Hz() {
        val b = ImuWindowBuilder()
        // events every 10 ms (100 Hz) for 2 s must still give about 100 rows, not 200
        feed(b, 200, 0f, 0f, g, stepNanos = 10_000_000L)
        assertEquals(100.0, b.fill.toDouble(), 2.0)
        val c = ImuWindowBuilder()
        // events every 40 ms (25 Hz) are held to fill the 50 Hz grid
        feed(c, 50, 0f, 0f, g, stepNanos = 40_000_000L)
        assertEquals(100.0, c.fill.toDouble(), 2.0)
    }

    @Test fun longGapResetsTheClockWithoutFillingRows() {
        val b = ImuWindowBuilder()
        val t = feed(b, 50, 0f, 0f, g)
        val before = b.fill
        feed(b, 1, 0f, 0f, g, t + 60_000_000_000L)
        assertEquals(before + 1, b.fill)
    }

    @Test fun nonFiniteValuesAreIgnored() {
        val b = ImuWindowBuilder()
        b.onAccelerometer(Float.NaN, 0f, g, 0L)
        b.onAccelerometer(0f, 0f, g, -1L)
        assertEquals(0, b.fill)
    }

    @Test fun resetClearsTheWindow() {
        val b = ImuWindowBuilder()
        feed(b, 128, 0f, 0f, g)
        b.reset()
        assertEquals(0, b.fill)
        assertNull(b.windowIfDue())
    }

    @Test fun axisMapSwapsAndFlips() {
        val b = ImuWindowBuilder(AxisMap(order = intArrayOf(2, 0, 1), sign = floatArrayOf(1f, -1f, 1f)))
        feed(b, 128, g, 0f, 0f) // phone x = 1 g -> har[1] = -phone[0]
        val w = b.windowIfDue()!!
        val last = 127 * 9
        assertEquals(0f, w[last + 6], 1e-4f)
        assertEquals(-1f, w[last + 7], 1e-4f)
        assertEquals(0f, w[last + 8], 1e-4f)
    }
}

class PredictionTest {
    @Test fun picksTheLargestScore() {
        val p = Prediction.fromScores(floatArrayOf(0.05f, 0.05f, 0.1f, 0.6f, 0.15f, 0.05f))
        assertEquals(Activity.Sitting, p.activity)
        assertEquals(0.6f, p.confidence, 0f)
        assertEquals(6, p.scores.size)
    }

    @Test fun onlyWalkingClassesCountAsMoving() {
        assertEquals(listOf(true, true, true, false, false, false), Activity.entries.map { it.moving })
    }

    @Test(expected = IllegalArgumentException::class) fun wrongSizeIsRejected() {
        Prediction.fromScores(floatArrayOf(1f, 0f))
    }
}
