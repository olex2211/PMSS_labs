package com.example.lab1.flashlight

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab1.FlashlightApp
import com.example.lab1.decision.CloudCommandTracker
import com.example.lab1.decision.DecisionEngine
import com.example.lab1.decision.LightCommand
import com.example.lab1.decision.LightDecision
import com.example.lab1.decision.LightSource
import com.example.lab1.helpers.*
import com.example.lab1.helpers.ble.*
import com.example.lab1.repository.CloudConfig
import com.example.lab1.repository.OfflineException
import com.example.lab1.repository.SensorReading
import com.example.lab1.repository.StorageConfig
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
    private val engine = DecisionEngine()
    private val cloudCommands = CloudCommandTracker()
    private val vibrator = VibrationActuator(application)
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
    private var commandJob: Job? = null
    private var lastRecordMs = 0L
    private var pendingNotified = false
    private var lastUiNanos = 0L
    private var peakG = 0f
    private var active = false

    init {
        _state.update { it.copy(thresholdG = detector.thresholdG, sensorAvailable = sensor.available,
            sensorName = sensor.name, torch = flashlight.state, cameraPermission = settings.cameraAllowed(),
            online = network.isOnlineNow(), vibratorAvailable = vibrator.available) }
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
                        applyCommand(LightCommand(enabled, LightSource.Ble))
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
            FlashlightAction.ToggleLight -> applyCommand(LightCommand(!state.value.torch.enabled, LightSource.Manual))
            FlashlightAction.TestVibration -> vibrate(LightSource.Manual.name)
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
        commandJob?.cancel(); commandJob = null
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
        // Room gets one row per second for the time series; a shake is stored at once and sent at once.
        if (!sample.triggered && reading.timestamp - lastRecordMs >= StorageConfig.RECORD_INTERVAL_MS) {
            lastRecordMs = reading.timestamp
            viewModelScope.launch { repository.record(reading) }
        }
        if (sample.triggered) {
            lastRecordMs = reading.timestamp
            _state.update { it.copy(shakeCount = it.shakeCount + 1,
                events = (listOf(ShakeEvent(timeFormat.format(Date()), sample.magnitudeG)) + it.events).take(5)) }
            viewModelScope.launch {
                repository.record(reading)
                syncPending()
            }
            engine.onShake(state.value.automatic, flashlight.state.enabled)?.let(::applyDecision)
            if (connector.connection.value == ConnectionStatus.Connected) {
                write(FlashlightBleProfile.shake(v.x, v.y, v.z, sample.magnitudeG), FlashlightBleProfile.TELEMETRY)
            }
        }
        if (lastUiNanos == 0L || v.timestampNanos - lastUiNanos >= 50_000_000L || sample.triggered) {
            lastUiNanos = v.timestampNanos
            _state.update { it.copy(x = v.x, y = v.y, z = v.z, magnitudeG = sample.magnitudeG, peakG = peakG, hasReading = true) }
        }
    }

    /** Reacts to network changes and sends the rows Room still holds as unsynced every [CloudConfig.UPLOAD_INTERVAL_MS]. */
    private fun startCloud() {
        networkJob = viewModelScope.launch {
            network.online().collect { online ->
                Log.i(CloudConfig.TAG, "NETWORK " + if (online) "online" else "offline")
                _state.update { it.copy(online = online) }
                if (online) syncPending()
            }
        }
        uploadJob = viewModelScope.launch {
            while (true) {
                delay(CloudConfig.UPLOAD_INTERVAL_MS)
                syncPending()
            }
        }
        commandJob = viewModelScope.launch {
            while (true) {
                delay(CloudConfig.COMMAND_POLL_MS)
                pollCommand()
            }
        }
    }

    /** Cloud-to-device: polls control/flashlight and applies a value only when it changes in the cloud. */
    private suspend fun pollCommand() {
        if (!state.value.cloudEnabled || !state.value.online) return
        repository.readCommand()
            .onSuccess { value ->
                _state.update { it.copy(cloudCommand = value) }
                cloudCommands.onRead(value)?.let { enabled ->
                    Log.i(CloudConfig.TAG, "COMMAND control/flashlight=$enabled")
                    actuatorLog("CLOUD control/flashlight = ${if (enabled) "ON" else "OFF"}")
                    applyCommand(LightCommand(enabled, LightSource.Cloud))
                }
            }
            .onFailure { error -> Log.i(CloudConfig.TAG, "COMMAND read failed: ${error.message}") }
    }

    /** Every light change goes through [DecisionEngine]; this is the only place that drives the torch from rules. */
    private fun applyCommand(command: LightCommand) = applyDecision(engine.onCommand(command, flashlight.state.enabled))

    private fun applyDecision(result: LightDecision) {
        if (result.changed) {
            flashlight.setEnabled(result.enabled)
            actuatorLog("LIGHT ${if (result.enabled) "ON" else "OFF"} (${result.source.name})")
            persistEvent(result.source.name, if (result.enabled) "LIGHT_ON" else "LIGHT_OFF")
            _state.update { it.copy(lastLightSource = result.source, lastLightTime = timeFormat.format(Date())) }
        }
        if (result.vibrate) vibrate(result.source.name)
    }

    private fun vibrate(source: String) {
        if (vibrator.pulse()) {
            actuatorLog("VIBRATE ${VibrationActuator.PULSE_MS} ms")
            persistEvent(source, "VIBRATE")
        } else {
            actuatorLog("VIBRATE unavailable: no vibrator")
        }
    }

    /** Every actuator action also goes to Room so the history survives a restart. */
    private fun persistEvent(source: String, action: String) {
        viewModelScope.launch { repository.logActuator(source, action) }
    }

    /** Actuator events go to Logcat (tag [ACTUATOR_TAG]) and to the on-screen actuator log, apart from the BLE terminal. */
    private fun actuatorLog(text: String) {
        Log.i(ACTUATOR_TAG, text)
        _state.update { it.copy(actuatorLog = (it.actuatorLog + TerminalEntry(timeFormat.format(Date()), text)).takeLast(20)) }
    }

    private fun syncNow() {
        if (!state.value.cloudEnabled) { message("Cloud sync is off"); return }
        viewModelScope.launch { syncPending() }
    }

    /** Sends the rows that Room holds as unsynced; the indicator mirrors the result. */
    private suspend fun syncPending() {
        if (!state.value.cloudEnabled) return
        val wasPending = state.value.syncStatus == SyncStatus.Pending
        _state.update { it.copy(syncStatus = SyncStatus.Syncing) }
        repository.syncPending(online = state.value.online)
            .onSuccess { result ->
                if (result.sent > 0 || result.left > 0) {
                    Log.i(CloudConfig.TAG, "SYNC sent=${result.sent} left=${result.left} key=${result.lastKey}")
                }
                if (result.left == 0) pendingNotified = false
                _state.update { it.copy(
                    syncStatus = if (result.left > 0) SyncStatus.Pending else SyncStatus.Synced,
                    uploadedCount = it.uploadedCount + result.sent,
                    lastCloudKey = result.lastKey ?: it.lastCloudKey,
                    lastSyncTime = if (result.sent > 0) timeFormat.format(Date()) else it.lastSyncTime) }
                if (wasPending && result.left == 0 && result.sent > 0) message("Pending readings sent: ${result.sent}")
            }
            .onFailure { error -> markPending(error) }
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

    companion object {
        private const val OPERATION_TIMEOUT_MS = 5_000L
        const val ACTUATOR_TAG = "FlashlightActuator"
    }
}
