package com.example.lab1.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Insert
    suspend fun insert(record: SensorRecord): Long

    /** Newest first. Room re-emits the list every time the table changes, which drives the chart. */
    @Query("SELECT * FROM sensor_records ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun latest(limit: Int): Flow<List<SensorRecord>>

    /** Oldest first, so the cloud receives rows in the order they were measured. */
    @Query("SELECT * FROM sensor_records WHERE synced = 0 ORDER BY timestamp ASC, id ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<SensorRecord>

    @Query("UPDATE sensor_records SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT COUNT(*) FROM sensor_records WHERE synced = 0")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sensor_records WHERE synced = 0")
    suspend fun pendingNow(): Int

    @Query("SELECT COUNT(*) FROM sensor_records")
    fun count(): Flow<Int>

    /** Keeps the newest [keep] synced rows. Rows that still wait for the cloud are never deleted here. */
    @Query(
        "DELETE FROM sensor_records WHERE synced = 1 AND id NOT IN " +
            "(SELECT id FROM sensor_records WHERE synced = 1 ORDER BY timestamp DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimSynced(keep: Int): Int

    /** Safety cap for a long offline period: drops the oldest unsynced rows beyond [keep]. */
    @Query(
        "DELETE FROM sensor_records WHERE synced = 0 AND id NOT IN " +
            "(SELECT id FROM sensor_records WHERE synced = 0 ORDER BY timestamp DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimUnsynced(keep: Int): Int

    @Query("DELETE FROM sensor_records")
    suspend fun clear()
}
