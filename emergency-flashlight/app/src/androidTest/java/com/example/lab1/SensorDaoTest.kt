package com.example.lab1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lab1.repository.ActuatorEvent
import com.example.lab1.repository.AppDatabase
import com.example.lab1.repository.SensorRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Room/SQLite engine on the device with an in-memory database. */
@RunWith(AndroidJUnit4::class)
class SensorDaoTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private fun row(t: Long, g: Float = 1f, shake: Boolean = false, synced: Boolean = false) =
        SensorRecord(timestamp = t, sensorValue = g, x = 0f, y = 0f, z = 9.81f, actionTriggered = shake, synced = synced)

    @Test fun latestReturnsTheNewestRowsFirstAndRespectsTheLimit() = runBlocking {
        val dao = db.sensorDao()
        (1L..60L).forEach { dao.insert(row(it)) }
        val latest = dao.latest(50).first()
        assertEquals(50, latest.size)
        assertEquals(60L, latest.first().timestamp)
        assertEquals(11L, latest.last().timestamp)
    }

    @Test fun unsyncedAreOldestFirstAndMarkSyncedRemovesThemFromTheQueue() = runBlocking {
        val dao = db.sensorDao()
        val ids = listOf(30L, 10L, 20L).map { dao.insert(row(it)) }
        assertEquals(listOf(10L, 20L, 30L), dao.unsynced(10).map { it.timestamp })
        dao.markSynced(ids[1]) // ids[1] is the row with timestamp 10, the oldest one
        assertEquals(listOf(20L, 30L), dao.unsynced(10).map { it.timestamp })
        assertEquals(2, dao.pendingNow())
    }

    @Test fun trimSyncedKeepsTheNewestAndNeverTouchesUnsyncedRows() = runBlocking {
        val dao = db.sensorDao()
        (1L..10L).forEach { dao.insert(row(it, synced = true)) }
        (11L..13L).forEach { dao.insert(row(it, synced = false)) }
        assertEquals(7, dao.trimSynced(3))
        assertEquals(6, dao.count().first())
        assertEquals(3, dao.pendingNow())
    }

    @Test fun flowEmitsAgainAfterAnInsert() = runBlocking {
        val dao = db.sensorDao()
        assertTrue(dao.latest(5).first().isEmpty())
        dao.insert(row(1, g = 2.5f, shake = true))
        val rows = dao.latest(5).first()
        assertEquals(1, rows.size)
        assertTrue(rows.single().actionTriggered)
    }

    @Test fun actuatorEventsAreOrderedAndTrimmed() = runBlocking {
        val dao = db.actuatorEventDao()
        (1L..5L).forEach { dao.insert(ActuatorEvent(timestamp = it, source = "Shake", action = "LIGHT_ON")) }
        assertEquals(listOf(5L, 4L), dao.latest(2).first().map { it.timestamp })
        dao.trim(2)
        assertEquals(listOf(5L, 4L), dao.latest(10).first().map { it.timestamp })
    }
}
