package com.example.lab1.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineException : Exception("No network connection")

/** Outcome of one sync pass: rows sent now, rows still waiting, key of the last stored cloud record. */
data class SyncResult(val sent: Int, val left: Int, val lastKey: String?)

/**
 * The only place that touches Room and the cloud. One instance lives in [com.example.lab1.FlashlightApp].
 * Rule: a row is written to Room first and uploaded afterwards; a failed upload leaves `synced = false`,
 * so the data survives offline periods and application restarts and is sent later.
 */
class SensorRepository(
    private val api: CloudApi,
    private val dao: SensorDao,
    private val events: ActuatorEventDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncLock = Mutex()

    /** Number of rows that the cloud has not accepted yet. */
    val pending: Flow<Int> = dao.pendingCount()
    val totalRecords: Flow<Int> = dao.count()

    /** Newest [limit] rows; Room emits again after every insert. */
    fun history(limit: Int = StorageConfig.CHART_POINTS): Flow<List<SensorRecord>> = dao.latest(limit)

    fun actuatorEvents(limit: Int): Flow<List<ActuatorEvent>> = events.latest(limit)

    /** Step 1 of local-first: the row exists in Room before any network call. */
    suspend fun record(reading: SensorReading): Long = withContext(ioDispatcher) {
        dao.insert(reading.toRecord())
    }

    suspend fun logActuator(source: String, action: String, timestamp: Long = System.currentTimeMillis()) {
        withContext(ioDispatcher) {
            events.insert(ActuatorEvent(timestamp = timestamp, source = source, action = action))
            events.trim(StorageConfig.RETAIN_EVENTS)
        }
    }

    /** Saves the reading locally and then tries to send everything that is not synced yet. */
    suspend fun save(reading: SensorReading, online: Boolean = true): Result<SyncResult> {
        record(reading)
        return syncPending(online)
    }

    /**
     * Step 2: sends unsynced rows oldest first and marks each one after the cloud accepted it.
     * Stops at the first failure and keeps the rest pending. Fails only when nothing could be sent.
     */
    suspend fun syncPending(online: Boolean = true): Result<SyncResult> = withContext(ioDispatcher) {
        if (!online) return@withContext Result.failure(OfflineException())
        syncLock.withLock {
            var sent = 0
            var lastKey: String? = null
            var failure: Throwable? = null
            while (failure == null) {
                val batch = dao.unsynced(StorageConfig.SYNC_BATCH)
                if (batch.isEmpty()) break
                for (record in batch) {
                    val result = attempt { api.upload(record.toPayload()) }
                    if (result.isFailure) {
                        failure = result.exceptionOrNull()
                        break
                    }
                    dao.markSynced(record.id)
                    lastKey = result.getOrNull()?.name
                    sent++
                }
            }
            dao.trimSynced(StorageConfig.RETAIN_SYNCED)
            dao.trimUnsynced(StorageConfig.MAX_UNSYNCED)
            val left = dao.pendingNow()
            val error = failure
            if (error != null && sent == 0) Result.failure(error) else Result.success(SyncResult(sent, left, lastKey))
        }
    }

    suspend fun clearHistory() = withContext(ioDispatcher) {
        dao.clear()
        events.clear()
    }

    /** Reads the cloud command for the flashlight. Null means the node or value is not set. */
    suspend fun readCommand(): Result<Boolean?> = withContext(ioDispatcher) {
        attempt {
            val response = api.readControl()
            check(response.isSuccessful) { "HTTP ${response.code()}" }
            response.body()?.flashlight
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
