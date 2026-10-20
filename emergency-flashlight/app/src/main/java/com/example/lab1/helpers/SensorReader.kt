package com.example.lab1.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class Acceleration(val x: Float, val y: Float, val z: Float, val timestampNanos: Long)

class SensorReader(context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available: Boolean get() = sensor != null
    val name: String get() = sensor?.name ?: "Accelerometer unavailable"

    fun readings(): Flow<Acceleration> = callbackFlow {
        val accelerometer = sensor ?: error("Accelerometer unavailable")
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) {
                    trySend(Acceleration(event.values[0], event.values[1], event.values[2], event.timestamp))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        check(manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)) {
            "Unable to start the accelerometer"
        }
        awaitClose { manager.unregisterListener(listener) }
    }
}
