package com.example.lab1

import com.example.lab1.helpers.ble.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockBleTest {
    @Test fun scanFindsDeviceAfterExactlyTwoSeconds() = runTest {
        val result = async { MockBleScanner().scan().first() }
        advanceTimeBy(1_999)
        assertFalse(result.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(FlashlightBleProfile.device), result.await())
    }

    @Test fun cancelledScanDoesNotDeliverADevice() = runTest {
        var found = false
        val scan = launch { MockBleScanner().scan().collect { found = true } }
        advanceTimeBy(1_000)
        scan.cancelAndJoin()
        advanceUntilIdle()
        assertFalse(found)
    }

    @Test fun lifecycleReadWriteAndNotifyUsePeripheralState() = runTest {
        val connector = MockBleConnector()
        val connection = launch { connector.connect(FlashlightBleProfile.device) }
        runCurrent()
        assertEquals(ConnectionStatus.Connecting, connector.connection.value)
        advanceUntilIdle()
        connection.join()
        assertEquals(ConnectionStatus.Connected, connector.connection.value)
        assertEquals("STATUS OFF", connector.readStatus().payload)
        val notification = async(start = CoroutineStart.UNDISPATCHED) { connector.notifications.first() }
        connector.sendData("LIGHT ON")
        assertEquals(BlePacket(FlashlightBleProfile.STATUS, "STATUS ON"), notification.await())
        assertEquals("STATUS ON", connector.readStatus().payload)
        connector.sendData("LIGHT OFF")
        assertEquals("STATUS OFF", connector.readStatus().payload)
        connector.disconnect()
        assertEquals(ConnectionStatus.Disconnected, connector.connection.value)
    }

    @Test fun telemetryAcknowledgesTheActualMeasurement() = runTest {
        val connector = MockBleConnector()
        connector.connect(FlashlightBleProfile.device)
        val payload = FlashlightBleProfile.shake(30f, -2f, 9.80665f, 3.22f)
        val notify = async(start = CoroutineStart.UNDISPATCHED) { connector.notifications.first() }
        connector.sendData(payload)
        assertEquals("ACK $payload", notify.await().payload)
        assertEquals("STATUS OFF", connector.readStatus().payload)
    }

    @Test fun disconnectedWriteAndInvalidCommandAreRejected() = runTest {
        val connector = MockBleConnector()
        try { connector.sendData("LIGHT ON"); fail("Disconnected write accepted") } catch (_: IllegalStateException) { }
        connector.connect(FlashlightBleProfile.device)
        for (payload in listOf("LIGHT MAYBE", "SHAKE NaN 0 0 1", "SHAKE 1 2", "SHAKE 0 0 0 -1")) {
            try { connector.sendData(payload); fail("Invalid packet accepted: $payload") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("STATUS OFF", connector.readStatus().payload)
    }

    @Test fun cancelDuringConnectingLeavesDisconnected() = runTest {
        val connector = MockBleConnector()
        val job = launch { connector.connect(FlashlightBleProfile.device) }
        runCurrent()
        job.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(ConnectionStatus.Disconnected, connector.connection.value)
        connector.connect(FlashlightBleProfile.device)
        assertEquals(ConnectionStatus.Connected, connector.connection.value)
    }

    @Test fun disconnectInvalidatesPendingWrite() = runTest {
        val connector = MockBleConnector()
        connector.connect(FlashlightBleProfile.device)
        var rejected = false
        val write = launch {
            try { connector.sendData("LIGHT ON") } catch (_: IllegalStateException) { rejected = true }
        }
        runCurrent()
        connector.disconnect()
        advanceUntilIdle()
        write.join()
        assertTrue(rejected)
        connector.connect(FlashlightBleProfile.device)
        assertEquals("STATUS OFF", connector.readStatus().payload)
    }

    @Test fun profileUuidsAreUniqueAndValid() {
        val uuids = listOf(FlashlightBleProfile.SERVICE, FlashlightBleProfile.COMMAND, FlashlightBleProfile.STATUS, FlashlightBleProfile.TELEMETRY)
        assertEquals(4, uuids.map(java.util.UUID::fromString).toSet().size)
        assertNull(FlashlightBleProfile.parseStatus("STATUS MAYBE"))
    }
}
