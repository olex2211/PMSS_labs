package com.example.lab1.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActuatorEventDao {
    @Insert
    suspend fun insert(event: ActuatorEvent): Long

    @Query("SELECT * FROM actuator_events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun latest(limit: Int): Flow<List<ActuatorEvent>>

    @Query(
        "DELETE FROM actuator_events WHERE id NOT IN " +
            "(SELECT id FROM actuator_events ORDER BY timestamp DESC, id DESC LIMIT :keep)"
    )
    suspend fun trim(keep: Int): Int

    @Query("DELETE FROM actuator_events")
    suspend fun clear()
}
