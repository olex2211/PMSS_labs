package com.example.lab1.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** Keeps Android preferences and permission APIs outside the screen's ViewModel. */
class FlashlightSettings(private val context: Context) {
    private val preferences = context.getSharedPreferences("flashlight", Context.MODE_PRIVATE)
    fun threshold(): Float = preferences.getFloat("threshold", ShakeDetector.DEFAULT_THRESHOLD_G)
        .takeIf { it.isFinite() }?.coerceIn(ShakeDetector.MIN_THRESHOLD_G, ShakeDetector.MAX_THRESHOLD_G)
        ?: ShakeDetector.DEFAULT_THRESHOLD_G
    fun saveThreshold(value: Float) { preferences.edit().putFloat("threshold", value).apply() }
    fun cameraAllowed() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
