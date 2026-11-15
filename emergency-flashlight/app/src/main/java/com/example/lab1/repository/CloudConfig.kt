package com.example.lab1.repository

/** Cloud settings. The URL identifies the database and is not a secret; no API keys are used. */
object CloudConfig {
    const val BASE_URL = "https://emergency-flashlight-pmss-default-rtdb.firebaseio.com/"
    const val DEVICE_ID = "emergency-flashlight-16"
    const val SENSOR_TYPE = "accelerometer"
    const val UPLOAD_INTERVAL_MS = 5_000L
    /** Polling period for control/flashlight. Lab 4 uses polling; a streaming listener would react sooner. */
    const val COMMAND_POLL_MS = 2_000L
    const val MAX_PENDING = 200
    const val TAG = "FlashlightCloud"
}
