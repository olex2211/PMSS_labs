package com.example.lab1.analytics

import com.example.lab1.helpers.ShakeDetector
import com.example.lab1.repository.SensorRecord

/** One chart point: time, acceleration magnitude in g, and whether the row came from a shake. */
data class ChartPoint(val timestamp: Long, val value: Float, val shake: Boolean)

data class EventRow(val timestamp: Long, val source: String, val action: String)

data class AnalyticsState(
    /** Oldest first, at most [com.example.lab1.repository.StorageConfig.CHART_POINTS] items. */
    val points: List<ChartPoint> = emptyList(),
    val minG: Float = 0f,
    val maxG: Float = 0f,
    val averageG: Float = 0f,
    val shakesInWindow: Int = 0,
    val totalRecords: Int = 0,
    val pending: Int = 0,
    val events: List<EventRow> = emptyList(),
    val thresholdG: Float = ShakeDetector.DEFAULT_THRESHOLD_G,
)

sealed interface AnalyticsAction {
    /** The tab became visible: reload settings that the other screen may have changed. */
    data object Opened : AnalyticsAction
    data object ClearHistory : AnalyticsAction
}

sealed interface AnalyticsEvent {
    data class ShowMessage(val text: String) : AnalyticsEvent
}

/** Room returns the newest rows first; the chart needs them oldest first. */
fun chartPoints(rows: List<SensorRecord>): List<ChartPoint> =
    rows.asReversed().map { ChartPoint(it.timestamp, it.sensorValue, it.actionTriggered) }

/** Minimum, maximum, average and shake count of the visible window. */
fun AnalyticsState.withPoints(points: List<ChartPoint>): AnalyticsState {
    if (points.isEmpty()) return copy(points = points, minG = 0f, maxG = 0f, averageG = 0f, shakesInWindow = 0)
    val values = points.map { it.value }
    return copy(
        points = points,
        minG = values.min(),
        maxG = values.max(),
        averageG = values.average().toFloat(),
        shakesInWindow = points.count { it.shake },
    )
}
