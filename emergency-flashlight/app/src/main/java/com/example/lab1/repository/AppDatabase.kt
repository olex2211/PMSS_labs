package com.example.lab1.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SensorRecord::class, ActuatorEvent::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao
    abstract fun actuatorEventDao(): ActuatorEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** One database per process; the single repository in [com.example.lab1.FlashlightApp] owns it. */
        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "flashlight.db")
                .build()
                .also { instance = it }
        }
    }
}
