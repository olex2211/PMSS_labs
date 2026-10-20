package com.example.lab1.flashlight

import com.example.lab1.helpers.ShakeDetector
import com.example.lab1.helpers.TorchState
import com.example.lab1.helpers.ble.BleDevice
import com.example.lab1.helpers.ble.ConnectionStatus

data class ShakeEvent(val time: String = "", val magnitudeG: Float = 0f)
data class TerminalEntry(val time: String = "", val message: String = "")

data class FlashlightUiState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val magnitudeG: Float = 0f,
    val peakG: Float = 0f,
    val hasReading: Boolean = false,
    val thresholdG: Float = ShakeDetector.DEFAULT_THRESHOLD_G,
    val automatic: Boolean = true,
    val sensorAvailable: Boolean = true,
    val sensorActive: Boolean = false,
    val sensorName: String = "",
    val cameraPermission: Boolean = false,
    val torch: TorchState = TorchState(simulated = true),
    val shakeCount: Int = 0,
    val events: List<ShakeEvent> = emptyList(),
    val scanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
    val connection: ConnectionStatus = ConnectionStatus.Disconnected,
    val selectedDevice: BleDevice? = null,
    val transferring: Boolean = false,
    val remoteLight: Boolean? = null,
    val terminal: List<TerminalEntry> = emptyList(),
)

sealed interface FlashlightAction {
    data object Start : FlashlightAction
    data object Stop : FlashlightAction
    data class AutomaticChanged(val enabled: Boolean) : FlashlightAction
    data class ThresholdChanged(val value: Float) : FlashlightAction
    data object SaveThreshold : FlashlightAction
    data object ToggleLight : FlashlightAction
    data object RequestCamera : FlashlightAction
    data object PermissionResult : FlashlightAction
    data object ClearShakes : FlashlightAction
    data object Scan : FlashlightAction
    data class Connect(val device: BleDevice) : FlashlightAction
    data object Disconnect : FlashlightAction
    data object ReadStatus : FlashlightAction
    data class SendLight(val enabled: Boolean) : FlashlightAction
    data object SendReading : FlashlightAction
    data object ClearTerminal : FlashlightAction
}

sealed interface FlashlightEvent {
    data class ShowMessage(val text: String) : FlashlightEvent
    data object RequestCamera : FlashlightEvent
}
