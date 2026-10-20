package com.example.lab1.helpers

import android.util.Log

object BleLogger {
    const val TAG = "FlashlightBLE"
    fun log(message: String) { Log.i(TAG, message) }
}
