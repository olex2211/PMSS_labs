package com.example.lab1

import com.example.lab1.repository.ActuatorEvent
import com.example.lab1.repository.ActuatorEventDao
import com.example.lab1.repository.CloudApi
import com.example.lab1.repository.ControlResponse
import com.example.lab1.repository.OfflineException
import com.example.lab1.repository.SensorDao
import com.example.lab1.repository.SensorReading
import com.example.lab1.repository.SensorRecord
import com.example.lab1.repository.SensorPayload
import com.example.lab1.repository.SensorRepository
import com.example.lab1.repository.StorageConfig
import com.example.lab1.repository.UploadResponse
import com.example.lab1.repository.toPayload
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SensorRepositoryTest {
    private class FakeApi : CloudApi {
        var failing = false
        var failAfter: Int? = null
        var control: ControlResponse? = null
        val sent = mutableListOf<SensorPayload>()
        override suspend fun upload(payload: SensorPayload): UploadResponse {
            if (failing || (failAfter != null && sent.size >= failAfter!!)) throw IOException("no route to host")
            sent += payload
            return UploadResponse("key-${sent.size}")
        }
        override suspend fun readControl(): Response<ControlResponse?> {
            if (failing) throw IOException("no route to host")
            return Response.success(control)
        }
    }

    /** In-memory stand-in for the Room DAO with the same ordering and trimming rules. */
    private class FakeSensorDao : SensorDao {
        val rows = MutableStateFlow<List<SensorRecord>>(emptyList())
        private var nextId = 1L
        override suspend fun insert(record: SensorRecord): Long {
            val id = nextId++
            rows.value = rows.value + record.copy(id = id)
            return id
        }
        override fun latest(limit: Int): Flow<List<SensorRecord>> =
            rows.map { it.sortedWith(compareByDescending<SensorRecord> { r -> r.timestamp }.thenByDescending { r -> r.id }).take(limit) }
        override suspend fun unsynced(limit: Int) =
            rows.value.filter { !it.synced }.sortedWith(compareBy<SensorRecord> { it.timestamp }.thenBy { it.id }).take(limit)
        override suspend fun markSynced(id: Long) {
            rows.value = rows.value.map { if (it.id == id) it.copy(synced = true) else it }
        }
        override fun pendingCount(): Flow<Int> = rows.map { list -> list.count { !it.synced } }
        override suspend fun pendingNow() = rows.value.count { !it.synced }
        override fun count(): Flow<Int> = rows.map { it.size }
        override suspend fun trimSynced(keep: Int): Int = trim(synced = true, keep = keep)
        override suspend fun trimUnsynced(keep: Int): Int = trim(synced = false, keep = keep)
        override suspend fun clear() { rows.value = emptyList() }
        private fun trim(synced: Boolean, keep: Int): Int {
            val group = rows.value.filter { it.synced == synced }.sortedByDescending { it.timestamp }
            val drop = group.drop(keep).map { it.id }.toSet()
            rows.value = rows.value.filterNot { it.id in drop }
            return drop.size
        }
    }

    private class FakeEventDao : ActuatorEventDao {
        val rows = MutableStateFlow<List<ActuatorEvent>>(emptyList())
        override suspend fun insert(event: ActuatorEvent): Long {
            rows.value = rows.value + event.copy(id = rows.value.size + 1L)
            return rows.value.size.toLong()
        }
        override fun latest(limit: Int) = rows.map { it.sortedByDescending { e -> e.timestamp }.take(limit) }
        override suspend fun trim(keep: Int): Int {
            val drop = rows.value.sortedByDescending { it.timestamp }.drop(keep).map { it.id }.toSet()
            rows.value = rows.value.filterNot { it.id in drop }
            return drop.size
        }
        override suspend fun clear() { rows.value = emptyList() }
    }

    private fun reading(t: Long, shake: Boolean = false) =
        SensorReading(x = 1f, y = 2f, z = 9.81f, magnitudeG = 1f, shake = shake, timestamp = t)

    private class Env(val api: FakeApi = FakeApi(), val dao: FakeSensorDao = FakeSensorDao(), val events: FakeEventDao = FakeEventDao()) {
        val repository = SensorRepository(api, dao, events, UnconfinedTestDispatcher())
    }

    @Test fun recordWritesToTheDatabaseWithoutTouchingTheNetwork() = runTest {
        val env = Env()
        env.repository.record(reading(1))
        assertEquals(1, env.dao.rows.value.size)
        assertFalse(env.dao.rows.value.single().synced)
        assertTrue(env.api.sent.isEmpty())
    }

    @Test fun successfulSaveUploadsAndMarksTheRowSynced() = runTest {
        val env = Env()
        val result = env.repository.save(reading(1)).getOrThrow()
        assertEquals(1, result.sent)
        assertEquals(0, result.left)
        assertEquals("key-1", result.lastKey)
        assertTrue(env.dao.rows.value.single().synced)
    }

    @Test fun networkFailureKeepsTheRowLocalAndPending() = runTest {
        val env = Env(api = FakeApi().apply { failing = true })
        assertTrue(env.repository.save(reading(1)).isFailure)
        assertEquals(1, env.dao.rows.value.size)
        assertEquals(1, env.repository.pending.first())
    }

    @Test fun offlineFlagSavesLocallyAndSkipsTheNetwork() = runTest {
        val env = Env()
        val result = env.repository.save(reading(1), online = false)
        assertTrue(result.exceptionOrNull() is OfflineException)
        assertTrue(env.api.sent.isEmpty())
        assertEquals(1, env.repository.pending.first())
    }

    @Test fun syncSendsOldestFirstAfterRecovery() = runTest {
        val env = Env(api = FakeApi().apply { failing = true })
        listOf(3L, 1L, 2L).forEach { env.repository.record(reading(it)) }
        env.api.failing = false
        val result = env.repository.syncPending().getOrThrow()
        assertEquals(3, result.sent)
        assertEquals(listOf(1L, 2L, 3L), env.api.sent.map { it.timestamp })
        assertEquals(0, env.repository.pending.first())
    }

    @Test fun syncStopsAtTheFirstFailureAndKeepsTheRest() = runTest {
        val env = Env(api = FakeApi().apply { failAfter = 2 })
        listOf(1L, 2L, 3L, 4L).forEach { env.repository.record(reading(it)) }
        val result = env.repository.syncPending().getOrThrow()
        assertEquals(2, result.sent)
        assertEquals(2, result.left)
        assertEquals(listOf(3L, 4L), env.dao.rows.value.filter { !it.synced }.map { it.timestamp })
    }

    @Test fun syncFailsOnlyWhenNothingWasSent() = runTest {
        val env = Env(api = FakeApi().apply { failing = true })
        env.repository.record(reading(1))
        assertTrue(env.repository.syncPending().isFailure)
        assertEquals(1, env.repository.pending.first())
    }

    @Test fun pendingRowsSurviveInTheDatabaseUntilTheyAreSent() = runTest {
        val env = Env(api = FakeApi().apply { failing = true })
        env.repository.save(reading(1))
        // A new repository over the same database models an application restart.
        val restarted = SensorRepository(env.api.apply { failing = false }, env.dao, env.events, UnconfinedTestDispatcher())
        assertEquals(1, restarted.syncPending().getOrThrow().sent)
    }

    @Test fun retentionTrimsSyncedRowsButNeverUnsyncedOnes() = runTest {
        val env = Env()
        val total = StorageConfig.RETAIN_SYNCED + 5
        repeat(total) { env.dao.insert(reading(it.toLong()).toRecordSynced()) }
        env.repository.record(reading(total.toLong() + 1))
        env.api.failing = true
        env.repository.syncPending()
        // The failed pass trimmed the synced rows to the limit but kept the unsynced row.
        assertEquals(StorageConfig.RETAIN_SYNCED + 1, env.dao.rows.value.size)
        assertEquals(1, env.dao.pendingNow())
        env.api.failing = false
        env.repository.syncPending()
        assertEquals(1, env.api.sent.size)
        assertEquals(StorageConfig.RETAIN_SYNCED, env.dao.rows.value.size)
        assertEquals(0, env.dao.pendingNow())
    }

    @Test fun historyReturnsTheNewestRowsLimited() = runTest {
        val env = Env()
        (1L..5L).forEach { env.repository.record(reading(it)) }
        assertEquals(listOf(5L, 4L, 3L), env.repository.history(3).first().map { it.timestamp })
    }

    @Test fun actuatorEventsAreStoredNewestFirst() = runTest {
        val env = Env()
        env.repository.logActuator("Shake", "LIGHT_ON", 10)
        env.repository.logActuator("Manual", "LIGHT_OFF", 20)
        assertEquals(listOf("LIGHT_OFF", "LIGHT_ON"), env.repository.actuatorEvents(5).first().map { it.action })
    }

    @Test fun clearHistoryEmptiesBothTables() = runTest {
        val env = Env()
        env.repository.record(reading(1))
        env.repository.logActuator("Shake", "VIBRATE", 1)
        env.repository.clearHistory()
        assertTrue(env.dao.rows.value.isEmpty())
        assertTrue(env.events.rows.value.isEmpty())
    }

    @Test fun readCommandReturnsCloudFlashlightValue() = runTest {
        val env = Env(api = FakeApi().apply { control = ControlResponse(flashlight = true) })
        assertEquals(true, env.repository.readCommand().getOrNull())
    }

    @Test fun readCommandWithoutNodeReturnsNull() = runTest {
        val env = Env()
        assertTrue(env.repository.readCommand().isSuccess)
        assertNull(env.repository.readCommand().getOrNull())
    }

    @Test fun readCommandFailureIsReportedNotThrown() = runTest {
        val env = Env(api = FakeApi().apply { failing = true })
        assertTrue(env.repository.readCommand().isFailure)
    }

    @Test fun payloadJsonContainsDeviceTypeAxesAndTimestamp() {
        val json = Gson().toJsonTree(reading(1700000000000L, shake = true).toPayload()).asJsonObject
        assertEquals("emergency-flashlight-16", json["deviceId"].asString)
        assertEquals("accelerometer", json["sensorType"].asString)
        assertEquals(9.81f, json["z"].asFloat, 0.001f)
        assertTrue(json["shake"].asBoolean)
        assertEquals(1700000000000L, json["timestamp"].asLong)
    }

    private fun SensorReading.toRecordSynced() =
        SensorRecord(timestamp = timestamp, sensorValue = magnitudeG, x = x, y = y, z = z, actionTriggered = shake, synced = true)
}
