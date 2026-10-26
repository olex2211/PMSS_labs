package com.example.lab1.flashlight

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab1.FlashlightApp
import com.example.lab1.helpers.*
import com.example.lab1.helpers.ble.*
import com.example.lab1.repository.CloudConfig
import com.example.lab1.repository.OfflineException
import com.example.lab1.repository.SensorReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlashlightViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FlashlightUiState())
    val state: StateFlow<FlashlightUiState> = _state.asStateFlow()
    private val _events = Channel<FlashlightEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private val settings = FlashlightSettings(application)
    private val detector = ShakeDetector(settings.threshold())
    private val sensor = SensorReader(application)
    private val flashlight = FlashlightController(application) { torch ->
        _state.update { it.copy(torch = torch) }
    }
    private val scanner = MockBleScanner()
    private val connector: IBleConnector = MockBleConnector()
    private val repository = (application as FlashlightApp).repository
    private val network = NetworkMonitor(application)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var sensorJob: Job? = null
    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var transferJob: Job? = null
    private var uploadJob: Job? = null
    private var networkJob: Job? = null
    private var latestReading: SensorReading? = null
    private var pendingNotified = false
    private var lastUiNanos = 0L
    private var peakG = 0f
    private var active = false

    init {
        _state.update { it.copy(thresholdG = detector.thresholdG, sensorAvailable = sensor.available,
            sensorName = sensor.name, torch = flashlight.state, cameraPermission = settings.cameraAllowed(),
            online = network.isOnlineNow(), pendingCount = repository.pending.value) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.pending.collect { count -> _state.update { it.copy(pendingCount = count) } }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            connector.connection.collect { status -> _state.update { it.copy(connection = status) } }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            connector.notifications.collect { packet ->
                if (!active || connector.connection.value != ConnectionStatus.Connected) return@collect
                log("NOTIFY ${packet.characteristic} ← ${packet.payload}")
                if (packet.characteristic == FlashlightBleProfile.STATUS) {
                    FlashlightBleProfile.parseStatus(packet.payload)?.let { enabled ->
                        _state.update { it.copy(remoteLight = enabled) }
                        // Reuses the same controller as the button and the Lab 1 shake detector.
                        flashlight.setEnabled(enabled)
                    }
                }
            }
        }
    }

    /** The only entry point for screen and Activity actions. */
    fun onAction(action: FlashlightAction) {
        when (action) {
            FlashlightAction.Start -> start()
            FlashlightAction.Stop -> stop()
            is FlashlightAction.AutomaticChanged -> {
                detector.reset()
                _state.update { it.copy(automatic = action.enabled) }
            }
            is FlashlightAction.ThresholdChanged -> if (action.value.isFinite()) {
                detector.thresholdG = action.value.coerceIn(ShakeDetector.MIN_THRESHOLD_G, ShakeDetector.MAX_THRESHOLD_G)
                _state.update { it.copy(thresholdG = detector.thresholdG) }
            }
            FlashlightAction.SaveThreshold -> settings.saveThreshold(state.value.thresholdG)
            FlashlightAction.ToggleLight -> flashlight.setEnabled(!state.value.torch.enabled)
            FlashlightAction.RequestCamera -> event(FlashlightEvent.RequestCamera)
            FlashlightAction.PermissionResult -> _state.update { it.copy(cameraPermission = settings.cameraAllowed()) }
            FlashlightAction.ClearShakes -> {
                peakG = 0f
                _state.update { it.copy(events = emptyList(), shakeCount = 0, peakG = 0f) }
                message("Shake history cleared")
            }
            FlashlightAction.Scan -> scan()
            is FlashlightAction.Connect -> connect(action.device)
            FlashlightAction.Disconnect -> disconnect()
            FlashlightAction.ReadStatus -> transfer {
                log("READ ${FlashlightBleProfile.STATUS}")
                val packet = connector.readStatus()
                _state.update { it.copy(remoteLight = FlashlightBleProfile.parseStatus(packet.payload)) }
                log("READ OK ← ${packet.payload}")
            }
            is FlashlightAction.SendLight -> write(FlashlightBleProfile.lightCommand(action.enabled), FlashlightBleProfile.COMMAND)
            FlashlightAction.SendReading -> {
                val s = state.value
                if (s.hasReading) write(FlashlightBleProfile.sample(s.x, s.y, s.z, s.magnitudeG), FlashlightBleProfile.TELEMETRY)
                else message("Wait for an accelerometer reading first")
            }
            FlashlightAction.ClearTerminal -> _state.update { it.copy(terminal = emptyList()) }
            is FlashlightAction.CloudEnabledChanged -> _state.update {
                it.copy(cloudEnabled = action.enabled, syncStatus = if (action.enabled) it.syncStatus else SyncStatus.Idle)
            }
            FlashlightAction.SyncNow -> syncNow()
        }
    }

    private fun start() {
        if (active) return
        active = true
        detector.reset()
        lastUiNanos = 0L
        _state.update { it.copy(cameraPermission = settings.cameraAllowed()) }
        if (sensor.available) sensorJob = viewModelScope.launch {
            try {
                _state.update { it.copy(sensorActive = true) }
                sensor.readings().collect(::onReading)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { message(error.message ?: "Sensor error")
            } finally { _state.update { it.copy(sensorActive = false) } }
        }
        startCloud()
    }

    private fun stop() {
        active = false
        sensorJob?.cancel(); sensorJob = null
        uploadJob?.cancel(); uploadJob = null
        networkJob?.cancel(); networkJob = null
        latestReading = null
        detector.reset()
        scanJob?.cancel(); scanJob = null
        disconnect()
        flashlight.stop()
        _state.update { it.copy(sensorActive = false, scanning = false) }
    }

    private fun scan() {
        if (!active || state.value.scanning || state.value.connection != ConnectionStatus.Disconnected) return
        _state.update { it.copy(scanning = true, devices = emptyList(), selectedDevice = null) }
        log("SCAN started · mock · service ${FlashlightBleProfile.SERVICE}")
        scanJob = viewModelScope.launch {
            try {
                scanner.scan().collect { devices ->
                    _state.update { it.copy(devices = devices) }
                    log("SCAN found ${devices.joinToString { it.name }}")
                }
            } finally { _state.update { it.copy(scanning = false) } }
        }
    }

    private fun connect(device: BleDevice) {
        if (!active || state.value.scanning || connectJob?.isActive == true ||
            state.value.connection != ConnectionStatus.Disconnected || device !in state.value.devices) return
        _state.update { it.copy(selectedDevice = device) }
        connectJob = viewModelScope.launch {
            try {
                log("CONNECT ${device.id}")
                withTimeout(OPERATION_TIMEOUT_MS) { connector.connect(device) }
                log("CONNECTED ${device.name}")
                onAction(FlashlightAction.ReadStatus)
            } catch (error: TimeoutCancellationException) {
                connector.disconnect()
                failure(IllegalStateException("Connection timed out. Search again."))
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                connector.disconnect()
                failure(error)
            }
        }
    }

    private fun disconnect() {
        connectJob?.cancel(); connectJob = null
        transferJob?.cancel(); transferJob = null
        val wasConnected = connector.connection.value != ConnectionStatus.Disconnected
        connector.disconnect()
        _state.update { it.copy(connection = ConnectionStatus.Disconnected, transferring = false, remoteLight = null) }
        if (wasConnected) log("DISCONNECTED · pending operations cancelled")
    }

    private fun write(payload: String, characteristic: String) = transfer {
        log("WRITE $characteristic → $payload")
        connector.sendData(payload)
        log("WRITE OK → $payload")
    }

    private fun transfer(operation: suspend () -> Unit) {
        if (!active || connector.connection.value != ConnectionStatus.Connected) {
            message("Connect to the hub first"); return
        }
        if (transferJob?.isActive == true) { log("BUSY · retry after the current transfer completes"); return }
        _state.update { it.copy(transferring = true) }
        transferJob = viewModelScope.launch {
            try { withTimeout(OPERATION_TIMEOUT_MS) { operation() }
            } catch (error: TimeoutCancellationException) {
                failure(IllegalStateException("Transfer timed out. Retry the command."))
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { failure(error)
            } finally { _state.update { it.copy(transferring = false) } }
        }
    }

    private fun onReading(v: Acceleration) {
        if (!active || !listOf(v.x, v.y, v.z).all { it.isFinite() }) return
        val sample = detector.accept(v.x, v.y, v.z, v.timestampNanos)
        peakG = maxOf(peakG, sample.magnitudeG)
        val reading = SensorReading(x = v.x, y = v.y, z = v.z, magnitudeG = sample.magnitudeG,
            shake = sample.triggered, timestamp = System.currentTimeMillis())
        latestReading = reading.copy(shake = false)
        if (sample.triggered) {
            _state.update { it.copy(shakeCount = it.shakeCount + 1,
                events = (listOf(ShakeEvent(timeFormat.format(Date()), sample.magnitudeG)) + it.events).take(5)) }
            viewModelScope.launch { upload(reading) }
            if (state.value.automatic) flashlight.setEnabled(true)
            if (connector.connection.value == ConnectionStatus.Connected) {
                write(FlashlightBleProfile.shake(v.x, v.y, v.z, sample.magnitudeG), FlashlightBleProfile.TELEMETRY)
            }
        }
        if (lastUiNanos == 0L || v.timestampNanos - lastUiNanos >= 50_000_000L || sample.triggered) {
            lastUiNanos = v.timestampNanos
            _state.update { it.copy(x = v.x, y = v.y, z = v.z, magnitudeG = sample.magnitudeG, peakG = peakG, hasReading = true) }
        }
    }

    /** Reacts to network changes and sends the latest reading every [CloudConfig.UPLOAD_INTERVAL_MS]. */
    private fun startCloud() {
        networkJob = viewModelScope.launch {
            network.online().collect { online ->
                Log.i(CloudConfig.TAG, "NETWORK " + if (online) "online" else "offline")
                _state.update { it.copy(online = online) }
                if (online) flushPending()
            }
        }
        uploadJob = viewModelScope.launch {
            while (true) {
                delay(CloudConfig.UPLOAD_INTERVAL_MS)
                latestReading?.let { upload(it) }
            }
        }
    }

    private fun syncNow() {
        if (!state.value.cloudEnabled) { message("Cloud sync is off"); return }
        viewModelScope.launch {
            latestReading?.let { upload(it) }
            flushPending()
        }
    }

    private suspend fun upload(reading: SensorReading) {
        if (!state.value.cloudEnabled) return
        _state.update { it.copy(syncStatus = SyncStatus.Syncing) }
        repository.save(reading, online = state.value.online)
            .onSuccess { key ->
                pendingNotified = false
                Log.i(CloudConfig.TAG, "UPLOADED key=$key shake=${reading.shake} g=${"%.2f".format(Locale.US, reading.magnitudeG)}")
                _state.update { it.copy(syncStatus = SyncStatus.Synced, uploadedCount = it.uploadedCount + 1,
                    lastCloudKey = key, lastSyncTime = timeFormat.format(Date())) }
                flushPending()
            }
            .onFailure { error -> markPending(error) }
    }

    private suspend fun flushPending() {
        if (repository.pending.value == 0) return
        val sent = repository.syncPending()
        val left = repository.pending.value
        Log.i(CloudConfig.TAG, "SYNC pending sent=$sent left=$left")
        _state.update { it.copy(uploadedCount = it.uploadedCount + sent,
            syncStatus = if (left > 0) SyncStatus.Pending else SyncStatus.Synced,
            lastSyncTime = if (sent > 0) timeFormat.format(Date()) else it.lastSyncTime) }
        if (sent > 0 && left == 0) { pendingNotified = false; message("Pending readings sent: $sent") }
    }

    private fun markPending(error: Throwable) {
        Log.i(CloudConfig.TAG, "PENDING ${error.javaClass.simpleName}: ${error.message}")
        _state.update { it.copy(syncStatus = SyncStatus.Pending) }
        if (!pendingNotified) {
            pendingNotified = true
            message(if (error is OfflineException) "No network: readings kept as Pending" else "Cloud unavailable: readings kept as Pending")
        }
    }

    private fun log(text: String) {
        BleLogger.log(text)
        _state.update { it.copy(terminal = (it.terminal + TerminalEntry(timeFormat.format(Date()), text)).takeLast(100)) }
    }
    private fun failure(error: Exception) {
        val text = error.message ?: "BLE error"
        log("ERROR $text")
        message(text)
    }
    private fun message(text: String) = event(FlashlightEvent.ShowMessage(text))
    private fun event(event: FlashlightEvent) { viewModelScope.launch { _events.send(event) } }

    override fun onCleared() {
        stop()
        flashlight.close()
        _events.close()
        super.onCleared()
    }

    companion object { private const val OPERATION_TIMEOUT_MS = 5_000L }
}
