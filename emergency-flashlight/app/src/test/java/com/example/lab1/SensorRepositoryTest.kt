package com.example.lab1

import com.example.lab1.repository.CloudApi
import com.example.lab1.repository.ControlResponse
import com.example.lab1.repository.OfflineException
import com.example.lab1.repository.SensorPayload
import com.example.lab1.repository.SensorReading
import com.example.lab1.repository.SensorRepository
import com.example.lab1.repository.UploadResponse
import com.example.lab1.repository.toPayload
import com.google.gson.Gson
import retrofit2.Response
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SensorRepositoryTest {
    private class FakeApi : CloudApi {
        var failing = false
        var control: ControlResponse? = null
        val sent = mutableListOf<SensorPayload>()
        override suspend fun upload(payload: SensorPayload): UploadResponse {
            if (failing) throw IOException("no route to host")
            sent += payload
            return UploadResponse("key-${sent.size}")
        }
        override suspend fun readControl(): Response<ControlResponse?> {
            if (failing) throw IOException("no route to host")
            return Response.success(control)
        }
    }

    private fun reading(t: Long, shake: Boolean = false) =
        SensorReading(x = 1f, y = 2f, z = 9.81f, magnitudeG = 1f, shake = shake, timestamp = t)

    private fun repository(api: CloudApi, max: Int = 200) =
        SensorRepository(api, max, UnconfinedTestDispatcher())

    @Test fun successfulUploadReturnsKeyAndLeavesQueueEmpty() = runTest {
        val api = FakeApi()
        val repository = repository(api)
        assertEquals("key-1", repository.save(reading(1)).getOrNull())
        assertEquals(0, repository.pending.value)
    }

    @Test fun networkFailureKeepsRecordAsPending() = runTest {
        val api = FakeApi().apply { failing = true }
        val repository = repository(api)
        assertTrue(repository.save(reading(1)).isFailure)
        assertEquals(1, repository.pending.value)
    }

    @Test fun offlineFlagSkipsTheNetworkAndQueuesTheRecord() = runTest {
        val api = FakeApi()
        val repository = repository(api)
        val result = repository.save(reading(1), online = false)
        assertTrue(result.exceptionOrNull() is OfflineException)
        assertTrue(api.sent.isEmpty())
        assertEquals(1, repository.pending.value)
    }

    @Test fun syncPendingSendsInOrderAfterRecovery() = runTest {
        val api = FakeApi().apply { failing = true }
        val repository = repository(api)
        listOf(1L, 2L, 3L).forEach { repository.save(reading(it)) }
        api.failing = false
        assertEquals(3, repository.syncPending())
        assertEquals(listOf(1L, 2L, 3L), api.sent.map { it.timestamp })
        assertEquals(0, repository.pending.value)
    }

    @Test fun syncStopsAtFirstFailureAndKeepsTheRest() = runTest {
        val api = FakeApi().apply { failing = true }
        val repository = repository(api)
        listOf(1L, 2L).forEach { repository.save(reading(it)) }
        assertEquals(0, repository.syncPending())
        assertEquals(2, repository.pending.value)
    }

    @Test fun queueIsBoundedAndDropsTheOldestRecord() = runTest {
        val api = FakeApi().apply { failing = true }
        val repository = repository(api, max = 2)
        listOf(1L, 2L, 3L).forEach { repository.save(reading(it)) }
        assertEquals(2, repository.pending.value)
        api.failing = false
        repository.syncPending()
        assertEquals(listOf(2L, 3L), api.sent.map { it.timestamp })
    }

    @Test fun readCommandReturnsCloudFlashlightValue() = runTest {
        val api = FakeApi().apply { control = ControlResponse(flashlight = true) }
        assertEquals(true, repository(api).readCommand().getOrNull())
    }

    @Test fun readCommandWithoutNodeReturnsNull() = runTest {
        val api = FakeApi()
        assertTrue(repository(api).readCommand().isSuccess)
        assertNull(repository(api).readCommand().getOrNull())
    }

    @Test fun readCommandFailureIsReportedNotThrown() = runTest {
        val api = FakeApi().apply { failing = true }
        assertTrue(repository(api).readCommand().isFailure)
    }

    @Test fun payloadJsonContainsDeviceTypeAxesAndTimestamp() {
        val json = Gson().toJsonTree(reading(1700000000000L, shake = true).toPayload()).asJsonObject
        assertEquals("emergency-flashlight-16", json["deviceId"].asString)
        assertEquals("accelerometer", json["sensorType"].asString)
        assertEquals(9.81f, json["z"].asFloat, 0.001f)
        assertTrue(json["shake"].asBoolean)
        assertEquals(1700000000000L, json["timestamp"].asLong)
    }
}
