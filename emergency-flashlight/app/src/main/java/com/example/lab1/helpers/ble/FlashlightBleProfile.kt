package com.example.lab1.helpers.ble

import java.util.Locale

/** UTF-8 application protocol v1; UUIDs identify this educational custom GATT profile. */
object FlashlightBleProfile {
    const val SERVICE = "9b7e1600-6c5a-4d12-8f30-73b9b42a1000"
    const val COMMAND = "9b7e1601-6c5a-4d12-8f30-73b9b42a1000" // Write
    const val STATUS = "9b7e1602-6c5a-4d12-8f30-73b9b42a1000" // Read / Notify
    const val TELEMETRY = "9b7e1603-6c5a-4d12-8f30-73b9b42a1000" // Write / Notify ACK
    val device = BleDevice("virtual:flashlight:16", "Emergency Flashlight Hub · 16")

    fun lightCommand(enabled: Boolean) = if (enabled) "LIGHT ON" else "LIGHT OFF"
    fun status(enabled: Boolean) = if (enabled) "STATUS ON" else "STATUS OFF"
    fun parseStatus(payload: String): Boolean? = when (payload) {
        "STATUS ON" -> true
        "STATUS OFF" -> false
        else -> null
    }

    fun shake(x: Float, y: Float, z: Float, g: Float): String {
        require(listOf(x, y, z, g).all { it.isFinite() } && g >= 0)
        return String.format(Locale.US, "SHAKE %.3f %.3f %.3f %.3f", x, y, z, g)
    }

    fun sample(x: Float, y: Float, z: Float, g: Float) = shake(x, y, z, g).replaceFirst("SHAKE", "SAMPLE")

    fun isTelemetry(payload: String): Boolean {
        val parts = payload.split(' ')
        return parts.size == 5 && parts[0] in setOf("SHAKE", "SAMPLE") &&
            parts.drop(1).all { it.toFloatOrNull()?.isFinite() == true } &&
            parts.last().toFloat() >= 0f
    }
}
