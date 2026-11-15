package com.example.lab1

import com.example.lab1.analytics.AnalyticsState
import com.example.lab1.analytics.chartPoints
import com.example.lab1.analytics.withPoints
import com.example.lab1.repository.SensorRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsStateTest {
    private fun row(t: Long, g: Float, shake: Boolean = false) =
        SensorRecord(id = t, timestamp = t, sensorValue = g, x = 0f, y = 0f, z = 9.81f, actionTriggered = shake)

    @Test fun roomOrderIsReversedForTheChart() {
        val newestFirst = listOf(row(3, 1.0f), row(2, 1.0f), row(1, 1.0f))
        assertEquals(listOf(1L, 2L, 3L), chartPoints(newestFirst).map { it.timestamp })
    }

    @Test fun statisticsCoverTheVisibleWindow() {
        val state = AnalyticsState().withPoints(chartPoints(listOf(row(3, 3.0f, shake = true), row(2, 1.0f), row(1, 2.0f))))
        assertEquals(1.0f, state.minG, 0.0001f)
        assertEquals(3.0f, state.maxG, 0.0001f)
        assertEquals(2.0f, state.averageG, 0.0001f)
        assertEquals(1, state.shakesInWindow)
    }

    @Test fun emptyWindowGivesZeroStatistics() {
        val state = AnalyticsState().withPoints(emptyList())
        assertEquals(0f, state.maxG, 0f)
        assertEquals(0, state.shakesInWindow)
    }
}
