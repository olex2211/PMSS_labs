package com.example.lab1.repository

/** Domain model used by the ViewModel and the repository. */
data class SensorReading(
    val sensorType: String = CloudConfig.SENSOR_TYPE,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitudeG: Float,
    val shake: Boolean,
    val timestamp: Long,
)
