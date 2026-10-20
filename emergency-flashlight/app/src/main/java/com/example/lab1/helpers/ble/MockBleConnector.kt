package com.example.lab1.helpers.ble

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stateful virtual peripheral. Delays suspend rather than blocking the Android main thread. */
class MockBleConnector : IBleConnector {
    private val _connection = MutableStateFlow(ConnectionStatus.Disconnected)
    override val connection = _connection.asStateFlow()
    private val _notifications = MutableSharedFlow<BlePacket>(extraBufferCapacity = 16)
    override val notifications = _notifications.asSharedFlow()
    private val operations = Mutex()
    private var generation = 0L
    private var lightEnabled = false

    override suspend fun connect(device: BleDevice) = operations.withLock {
        check(_connection.value == ConnectionStatus.Disconnected) { "A connection is already active" }
        require(device == FlashlightBleProfile.device) { "Unknown device" }
        val session = ++generation
        _connection.value = ConnectionStatus.Connecting
        try {
            delay(CONNECT_DELAY_MS)
            check(session == generation) { "Connection cancelled" }
            _connection.value = ConnectionStatus.Connected
        } finally {
            if (_connection.value == ConnectionStatus.Connecting) disconnect()
        }
    }

    override fun disconnect() {
        generation++ // invalidates an in-flight operation even before its coroutine is cancelled
        _connection.value = ConnectionStatus.Disconnected
    }

    override suspend fun sendData(data: String) = operations.withLock {
        val session = requireConnection()
        val light = when (data) {
            "LIGHT ON" -> true
            "LIGHT OFF" -> false
            else -> null
        }
        require(light != null || FlashlightBleProfile.isTelemetry(data)) { "Unknown protocol v1 command" }
        delay(TRANSFER_DELAY_MS)
        check(session == generation) { "Connection lost" }
        if (light != null) {
            lightEnabled = light
            _notifications.emit(BlePacket(FlashlightBleProfile.STATUS, FlashlightBleProfile.status(lightEnabled)))
        } else {
            _notifications.emit(BlePacket(FlashlightBleProfile.TELEMETRY, "ACK $data"))
        }
    }

    override suspend fun readStatus(): BlePacket = operations.withLock {
        val session = requireConnection()
        delay(TRANSFER_DELAY_MS)
        check(session == generation) { "Connection lost" }
        BlePacket(FlashlightBleProfile.STATUS, FlashlightBleProfile.status(lightEnabled))
    }

    private fun requireConnection(): Long {
        check(_connection.value == ConnectionStatus.Connected) { "Connect to the hub first" }
        return generation
    }

    companion object {
        const val CONNECT_DELAY_MS = 700L
        const val TRANSFER_DELAY_MS = 150L
    }
}
