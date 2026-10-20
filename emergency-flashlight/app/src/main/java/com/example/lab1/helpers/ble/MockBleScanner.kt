package com.example.lab1.helpers.ble

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** No radio APIs: the lab explicitly requires a virtual device after two seconds. */
class MockBleScanner {
    fun scan(): Flow<List<BleDevice>> = flow {
        delay(SCAN_DELAY_MS)
        emit(listOf(FlashlightBleProfile.device))
    }

    companion object { const val SCAN_DELAY_MS = 2_000L }
}
