package com.example.lab1.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab1.FlashlightApp
import com.example.lab1.helpers.FlashlightSettings
import com.example.lab1.repository.StorageConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observes Room through the repository. It never reads the sensor: the Control screen writes the rows,
 * and Room's Flow pushes every change here, so the chart updates without any polling.
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FlashlightApp).repository
    private val settings = FlashlightSettings(application)

    private val _state = MutableStateFlow(AnalyticsState(thresholdG = settings.threshold()))
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()
    private val _events = Channel<AnalyticsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.history(StorageConfig.CHART_POINTS).collect { rows ->
                _state.update { it.withPoints(chartPoints(rows)) }
            }
        }
        viewModelScope.launch {
            repository.totalRecords.collect { total -> _state.update { it.copy(totalRecords = total) } }
        }
        viewModelScope.launch {
            repository.pending.collect { pending -> _state.update { it.copy(pending = pending) } }
        }
        viewModelScope.launch {
            repository.actuatorEvents(EVENT_ROWS).collect { rows ->
                _state.update { it.copy(events = rows.map { row -> EventRow(row.timestamp, row.source, row.action) }) }
            }
        }
    }

    /** The only entry point for the screen. */
    fun onAction(action: AnalyticsAction) {
        when (action) {
            AnalyticsAction.Opened -> _state.update { it.copy(thresholdG = settings.threshold()) }
            AnalyticsAction.ClearHistory -> viewModelScope.launch {
                repository.clearHistory()
                _events.send(AnalyticsEvent.ShowMessage("History cleared"))
            }
        }
    }

    private companion object {
        const val EVENT_ROWS = 8
    }
}
