package com.example.lab1.helpers

import kotlin.math.sqrt

/** Pure sensor logic: acceleration includes gravity, so a resting device measures 1 g. */
class ShakeDetector(thresholdG: Float = DEFAULT_THRESHOLD_G) {
    var thresholdG: Float = thresholdG
        set(value) {
            require(value.isFinite() && value in MIN_THRESHOLD_G..MAX_THRESHOLD_G)
            field = value
            reset()
        }

    private var armed = true
    private var lastShakeNanos: Long? = null

    init {
        require(thresholdG.isFinite() && thresholdG in MIN_THRESHOLD_G..MAX_THRESHOLD_G)
    }

    /** Hysteresis requires a return to rest; cooldown rejects multiple peaks of one shake. */
    fun accept(x: Float, y: Float, z: Float, timestampNanos: Long): ShakeSample {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || timestampNanos < 0) {
            return ShakeSample(0f, false)
        }
        val magnitudeG = (sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z) / GRAVITY).toFloat()
        if (magnitudeG < thresholdG * REARM_RATIO) armed = true
        val elapsed = lastShakeNanos?.let { timestampNanos - it }
        val cooledDown = elapsed == null || elapsed >= COOLDOWN_NANOS
        val triggered = armed && cooledDown && magnitudeG >= thresholdG
        if (triggered) {
            armed = false
            lastShakeNanos = timestampNanos
        }
        return ShakeSample(magnitudeG, triggered)
    }

    fun reset() {
        armed = true
        lastShakeNanos = null
    }

    companion object {
        const val GRAVITY = 9.80665f
        const val DEFAULT_THRESHOLD_G = 2.5f
        const val MIN_THRESHOLD_G = 1.5f
        const val MAX_THRESHOLD_G = 3.5f
        const val COOLDOWN_NANOS = 1_000_000_000L
        private const val REARM_RATIO = 0.7f
    }
}

data class ShakeSample(val magnitudeG: Float, val triggered: Boolean)
