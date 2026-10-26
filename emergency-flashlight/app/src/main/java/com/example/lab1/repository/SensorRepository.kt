package com.example.lab1.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineException : Exception("No network connection")

/**
 * The only place that talks to the cloud. One instance lives in [com.example.lab1.FlashlightApp],
 * so every screen shares the same pending queue. Records that cannot be sent are kept in memory
 * and retried; persistence across restarts is added with Room in a later lab.
 */
class SensorRepository(
    private val api: CloudApi,
    private val maxPending: Int = CloudConfig.MAX_PENDING,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val queueLock = Mutex()
    private val syncLock = Mutex()
    private val queue = ArrayDeque<SensorPayload>()
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    /** Uploads one reading. On any failure the record stays in the queue as Pending. */
    suspend fun save(reading: SensorReading, online: Boolean = true): Result<String> = withContext(ioDispatcher) {
        val payload = reading.toPayload()
        if (!online) {
            enqueue(payload)
            return@withContext Result.failure(OfflineException())
        }
        attempt { api.upload(payload).name }.onFailure { enqueue(payload) }
    }

    /** Sends queued records in order and stops at the first failure. Returns how many were sent. */
    suspend fun syncPending(): Int = withContext(ioDispatcher) {
        syncLock.withLock {
            var sent = 0
            while (true) {
                val next = queueLock.withLock { queue.firstOrNull() } ?: break
                if (attempt { api.upload(next) }.isFailure) break
                queueLock.withLock {
                    queue.removeFirstOrNull()
                    _pending.value = queue.size
                }
                sent++
            }
            sent
        }
    }

    private suspend fun enqueue(payload: SensorPayload) = queueLock.withLock {
        queue.addLast(payload)
        while (queue.size > maxPending) queue.removeFirst()
        _pending.value = queue.size
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
