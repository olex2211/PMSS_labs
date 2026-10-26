package com.example.lab1

import android.app.Application
import com.example.lab1.repository.CloudApiFactory
import com.example.lab1.repository.SensorRepository

/** Manual DI: one repository for the whole application. */
class FlashlightApp : Application() {
    val repository: SensorRepository by lazy { SensorRepository(CloudApiFactory.create()) }
}
