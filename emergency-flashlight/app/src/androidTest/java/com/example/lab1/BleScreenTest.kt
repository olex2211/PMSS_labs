package com.example.lab1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.lab1.flashlight.*
import com.example.lab1.helpers.ble.ConnectionStatus
import com.example.lab1.helpers.ble.FlashlightBleProfile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BleScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(rule.activity)[FlashlightViewModel::class.java]

    @Test fun scanConnectReadWriteNotifyAndDisconnect() {
        lateinit var vm: FlashlightViewModel
        rule.runOnIdle { vm = model() }
        rule.onNodeWithText("Search for devices").performScrollTo().performClick()
        rule.waitUntil(5_000) { vm.state.value.devices.isNotEmpty() }
        rule.onNodeWithText(FlashlightBleProfile.device.name).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Connect").performScrollTo().performClick()
        rule.waitUntil(5_000) { vm.state.value.connection == ConnectionStatus.Connected && !vm.state.value.transferring }
        rule.onNodeWithText("BLE ON").performScrollTo().performClick()
        rule.waitUntil(5_000) { vm.state.value.remoteLight == true && !vm.state.value.transferring }
        rule.runOnIdle { if (vm.state.value.torch.simulated) assertTrue(vm.state.value.torch.enabled) }
        rule.onNodeWithText("BLE OFF").performScrollTo().performClick()
        rule.waitUntil(5_000) { vm.state.value.remoteLight == false && !vm.state.value.transferring }
        rule.onNodeWithText("Read status").performScrollTo().performClick()
        rule.waitUntil(5_000) { vm.state.value.terminal.any { it.message == "READ OK ← STATUS OFF" } && !vm.state.value.transferring }
        rule.onNodeWithText("BLE terminal").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Disconnect").performScrollTo().performClick()
        rule.waitUntil(5_000) { vm.state.value.connection == ConnectionStatus.Disconnected }
    }

    @Test fun pauseCancelsScanAndPendingConnection() {
        lateinit var vm: FlashlightViewModel
        rule.runOnIdle { vm = model(); vm.onAction(FlashlightAction.Scan) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.runOnIdle { assertFalse(vm.state.value.scanning); assertFalse(vm.state.value.sensorActive) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.runOnIdle { vm.onAction(FlashlightAction.Scan) }
        rule.waitUntil(5_000) { vm.state.value.devices.isNotEmpty() }
        rule.runOnIdle { vm.onAction(FlashlightAction.Connect(vm.state.value.devices.first())) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.runOnIdle {
            assertEquals(ConnectionStatus.Disconnected, vm.state.value.connection)
            assertFalse(vm.state.value.transferring)
        }
    }

    @Test fun rotationRetainsStateAndStopsConnection() {
        lateinit var vm: FlashlightViewModel
        rule.runOnIdle { vm = model(); vm.onAction(FlashlightAction.Scan) }
        rule.waitUntil(5_000) { vm.state.value.devices.isNotEmpty() }
        rule.runOnIdle { vm.onAction(FlashlightAction.Connect(vm.state.value.devices.first())) }
        rule.waitUntil(5_000) { vm.state.value.connection == ConnectionStatus.Connected }
        rule.activityRule.scenario.recreate()
        rule.runOnIdle {
            assertSame(vm, model())
            assertTrue(vm.state.value.devices.isNotEmpty())
            assertEquals(ConnectionStatus.Disconnected, vm.state.value.connection)
        }
    }

}
