package com.example.lab1.decision

/** Who asked for a light change. Shake, manual, BLE and cloud commands all pass through [DecisionEngine]. */
enum class LightSource { Shake, Manual, Ble, Cloud }

data class LightCommand(val enabled: Boolean, val source: LightSource)

/** Result of one decision. [changed] is false when the light already has the requested state. */
data class LightDecision(
    val enabled: Boolean,
    val changed: Boolean,
    val source: LightSource,
    val vibrate: Boolean,
)

/**
 * Pure rules for the flashlight actuator (no Android APIs, so it is unit-tested on the JVM).
 *
 * Policy:
 * - Explicit commands (manual, BLE, cloud) are applied in arrival order; the latest one wins.
 * - A shake only requests ON while automatic mode is enabled. A shake never turns the light OFF,
 *   so repeated detections of one shake or of later shakes cannot toggle it.
 * - Every accepted shake pulses the vibrator, even when the light is already on.
 * - The light has no timer: it stays on until an explicit OFF command arrives.
 */
class DecisionEngine {
    /** Returns null when automatic mode is disabled and the shake must be ignored. */
    fun onShake(automatic: Boolean, currentlyOn: Boolean): LightDecision? {
        if (!automatic) return null
        return LightDecision(enabled = true, changed = !currentlyOn, source = LightSource.Shake, vibrate = true)
    }

    fun onCommand(command: LightCommand, currentlyOn: Boolean): LightDecision = LightDecision(
        enabled = command.enabled,
        changed = command.enabled != currentlyOn,
        source = command.source,
        vibrate = false,
    )
}

/**
 * Cloud-to-device commands. The app applies a value only when it differs from the last value read,
 * so a stale value is not replayed and a manual change is not overwritten on every poll.
 * The first successful read only sets the baseline.
 */
class CloudCommandTracker {
    private var last: Boolean? = null

    /** Returns the value to apply, or null when nothing changed, on the first read, or when the value is missing. */
    fun onRead(value: Boolean?): Boolean? {
        if (value == null) return null
        val previous = last
        last = value
        return if (previous != null && previous != value) value else null
    }
}
