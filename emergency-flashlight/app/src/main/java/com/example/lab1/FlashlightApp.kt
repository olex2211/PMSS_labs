package com.example.lab1

import android.app.Application
import com.example.lab1.repository.AppDatabase
import com.example.lab1.repository.CloudApiFactory
import com.example.lab1.repository.SensorRepository

/** Manual DI: one database and one repository for the whole application. */
class FlashlightApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: SensorRepository by lazy {
        SensorRepository(CloudApiFactory.create(), database.sensorDao(), database.actuatorEventDao())
    }
}
