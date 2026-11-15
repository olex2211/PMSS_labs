package com.example.lab1.repository

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One actuator action (Room table `actuator_events`), for example LIGHT_ON from a shake. */
@Entity(
    tableName = "actuator_events",
    indices = [Index("timestamp")],
)
data class ActuatorEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Who asked: Shake, Manual, Ble or Cloud. */
    val source: String,
    /** LIGHT_ON, LIGHT_OFF or VIBRATE. */
    val action: String,
)
