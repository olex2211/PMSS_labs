package com.example.lab1.repository

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row of the sensor time series (Room table `sensor_records`).
 * [synced] is false until the cloud has accepted the row, so the table is also the offline queue.
 */
@Entity(
    tableName = "sensor_records",
    indices = [Index("timestamp"), Index("synced")],
)
data class SensorRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Acceleration magnitude in g; this is the value drawn on the chart. */
    val sensorValue: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    /** True when the row was produced by a detected shake (the actuator was triggered). */
    val actionTriggered: Boolean,
    val synced: Boolean = false,
)

fun SensorReading.toRecord() = SensorRecord(
    timestamp = timestamp,
    sensorValue = magnitudeG,
    x = x,
    y = y,
    z = z,
    actionTriggered = shake,
)

fun SensorRecord.toReading() = SensorReading(
    x = x,
    y = y,
    z = z,
    magnitudeG = sensorValue,
    shake = actionTriggered,
    timestamp = timestamp,
)

fun SensorRecord.toPayload() = toReading().toPayload()
