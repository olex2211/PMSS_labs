package com.example.lab1.helpers.ml

import kotlin.math.min

/** Maps phone axes to the axes of the training data: har[i] = sign[i] * phone[order[i]]. */
data class AxisMap(val order: IntArray = intArrayOf(0, 1, 2), val sign: FloatArray = floatArrayOf(1f, 1f, 1f)) {
    fun apply(x: Float, y: Float, z: Float, out: FloatArray) {
        val phone = floatArrayOf(x, y, z)
        for (i in 0..2) out[i] = sign[i] * phone[order[i]]
    }

    companion object {
        val Identity = AxisMap()
    }
}

/**
 * Turns raw phone sensors into the model input: a window of [WINDOW] rows by [CHANNELS] columns at 50 Hz.
 * Row layout (the order used by the model's author): body_acc x y z, body_gyro x y z, total_acc x y z.
 * Acceleration is in g and the gyroscope in rad/s, as in the UCI HAR dataset.
 *
 * Android delivers samples at an uneven rate, so rows are produced on a fixed 20 ms grid, holding the last
 * value between events. Gravity is estimated with a one-pole low-pass filter (about 0.3 Hz) and subtracted
 * to get the body acceleration. Pure Kotlin, no Android classes, so it is unit-tested on the JVM.
 */
class ImuWindowBuilder(private val axisMap: AxisMap = AxisMap.Identity) {
    private val ring = FloatArray(WINDOW * CHANNELS)
    private var head = 0
    private var count = 0
    private var sinceWindow = 0
    private val gravity = FloatArray(3)
    private var gravityReady = false
    private val gyro = FloatArray(3)
    private val total = FloatArray(3)
    private var nextTickNanos = -1L

    /** Number of valid rows, up to [WINDOW]. */
    val fill: Int get() = min(count, WINDOW)

    fun onGyroscope(x: Float, y: Float, z: Float) {
        if (listOf(x, y, z).all { it.isFinite() }) axisMap.apply(x, y, z, gyro)
    }

    /** Accelerometer values in m/s^2 with the event time in nanoseconds. */
    fun onAccelerometer(x: Float, y: Float, z: Float, timestampNanos: Long) {
        if (!listOf(x, y, z).all { it.isFinite() } || timestampNanos < 0) return
        axisMap.apply(x / GRAVITY, y / GRAVITY, z / GRAVITY, total)
        if (!gravityReady) {
            total.copyInto(gravity)
            gravityReady = true
        }
        if (nextTickNanos < 0 || timestampNanos - nextTickNanos > GAP_RESET_NANOS) nextTickNanos = timestampNanos
        while (nextTickNanos <= timestampNanos) {
            appendRow()
            nextTickNanos += TICK_NANOS
        }
    }

    /** Returns the newest full window once per [STRIDE] new rows, oldest row first, otherwise null. */
    fun windowIfDue(): FloatArray? {
        if (count < WINDOW || sinceWindow < STRIDE) return null
        sinceWindow = 0
        val out = FloatArray(WINDOW * CHANNELS)
        val start = head * CHANNELS
        ring.copyInto(out, 0, start, ring.size)
        ring.copyInto(out, ring.size - start, 0, start)
        return out
    }

    fun reset() {
        head = 0; count = 0; sinceWindow = 0
        gravityReady = false
        nextTickNanos = -1L
        gyro.fill(0f)
    }

    private fun appendRow() {
        for (i in 0..2) gravity[i] += GRAVITY_ALPHA * (total[i] - gravity[i])
        val base = head * CHANNELS
        for (i in 0..2) {
            ring[base + i] = total[i] - gravity[i]
            ring[base + 3 + i] = gyro[i]
            ring[base + 6 + i] = total[i]
        }
        head = (head + 1) % WINDOW
        count++
        sinceWindow++
    }

    companion object {
        const val WINDOW = 128
        const val CHANNELS = 9
        const val STRIDE = 25
        const val SAMPLE_RATE_HZ = 50
        const val TICK_NANOS = 1_000_000_000L / SAMPLE_RATE_HZ
        const val GRAVITY = 9.80665f
        /** One-pole low-pass for 0.3 Hz at 50 Hz: alpha = 2*pi*fc*dt / (1 + 2*pi*fc*dt). */
        const val GRAVITY_ALPHA = 0.0363f
        private const val GAP_RESET_NANOS = 1_000_000_000L
    }
}
