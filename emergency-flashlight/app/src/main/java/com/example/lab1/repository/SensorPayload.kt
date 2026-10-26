package com.example.lab1.repository

/** JSON body sent to the cloud. Keeps the network format separate from the domain model. */
data class SensorPayload(
    val deviceId: String,
    val sensorType: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitudeG: Float,
    val shake: Boolean,
    val timestamp: Long,
)

fun SensorReading.toPayload(deviceId: String = CloudConfig.DEVICE_ID) = SensorPayload(
    deviceId = deviceId,
    sensorType = sensorType,
    x = x,
    y = y,
    z = z,
    magnitudeG = magnitudeG,
    shake = shake,
    timestamp = timestamp,
)
