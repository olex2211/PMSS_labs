package com.example.lab1.helpers.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionStatus { Disconnected, Connecting, Connected }
data class BleDevice(val id: String, val name: String)
data class BlePacket(val characteristic: String, val payload: String)

/** Transport boundary. A real GATT adapter can replace the mock without changing the screen. */
interface IBleConnector {
    val connection: StateFlow<ConnectionStatus>
    val notifications: Flow<BlePacket>
    suspend fun connect(device: BleDevice)
    fun disconnect()
    suspend fun sendData(data: String)
    suspend fun readStatus(): BlePacket
}
