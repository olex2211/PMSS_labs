package com.example.lab1.repository

/** Local storage parameters. Units are in the names; the reasons are in the lab 5 report. */
object StorageConfig {
    /** One reading per second goes to Room (the sensor itself runs at about 50 Hz). */
    const val RECORD_INTERVAL_MS = 1_000L

    /** The chart shows only the newest points so rendering stays cheap. */
    const val CHART_POINTS = 50

    /** Rows sent to the cloud per database query. */
    const val SYNC_BATCH = 200

    /** Newest rows kept after they were uploaded. */
    const val RETAIN_SYNCED = 5_000

    /** Cap for rows that could not be uploaded yet (about 5.5 hours offline at 1 row per second). */
    const val MAX_UNSYNCED = 20_000

    const val RETAIN_EVENTS = 500
}
