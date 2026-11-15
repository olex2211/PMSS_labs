package com.example.lab1.helpers

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Vibration feedback through VibratorManager (API 31+). Emulators may report no vibrator. */
class VibrationActuator(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    val available: Boolean = vibrator.hasVibrator()

    /** Returns false when the device has no vibrator, so the caller can report it. */
    fun pulse(durationMs: Long = PULSE_MS): Boolean {
        if (!available) return false
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        return true
    }

    companion object {
        const val PULSE_MS = 400L
    }
}
