package pl.luczka.todaywas.data.local.api

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.luczka.todaywas.data.local.dao.HabitCheckInDao
import pl.luczka.todaywas.data.local.dao.HabitDao
import pl.luczka.todaywas.data.local.database.TodayWasDatabase
import pl.luczka.todaywas.data.local.database.todayWasDatabaseCallbacks
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.util.RoomTransactionRunner

// End-to-end regression coverage for the habit cascade delete's order and transactional rollback:
// unlike LocalHabitDataSourceImplTest.kt (fakes, no real transaction semantics), this chains a
// real Room DB through real DAOs so a mid-cascade failure genuinely rolls back, and a thin
// recording wrapper proves the call order without faking the DAOs themselves.
@RunWith(RobolectricTestRunner::class)
class LocalHabitDataSourceIntegrationTest {

    private fun buildDatabase(context: Context): TodayWasDatabase = Room
        .databaseBuilder(context, TodayWasDatabase::class.java, "test-todaywas-${System.nanoTime()}.db")
        .allowMainThreadQueries()
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .addCallback(todayWasDatabaseCallbacks())
        .build()

    private class RecordingHabitDao(
        private val real: HabitDao,
        private val callOrderLog: MutableList<String>,
        private val throwOnSoftDelete: Boolean = false,
    ) : HabitDao by real {

        override suspend fun softDeleteById(
            id: String,
            deletedAt: Long,
        ) {
            callOrderLog += "habit"
            if (throwOnSoftDelete) throw RuntimeException("simulated failure")
            real.softDeleteById(id, deletedAt)
        }
    }

    private class RecordingHabitCheckInDao(
        private val real: HabitCheckInDao,
        private val callOrderLog: MutableList<String>,
    ) : HabitCheckInDao by real {

        override suspend fun softDeleteByHabitId(
            habitId: String,
            deletedAt: Long,
        ) {
            callOrderLog += "check-in"
            real.softDeleteByHabitId(habitId, deletedAt)
        }
    }

    @Test
    fun `should soft-delete check-ins before the habit and succeed when deleteHabitAndCheckIns is called`() = runTest {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildDatabase(context)
        db.habitDao().insert(
            HabitEntity(
                id = "habit-1",
                name = "Beers",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        db.habitCheckInDao().insertOne(
            HabitCheckInEntity(
                id = "check-in-1",
                habitId = "habit-1",
                date = "2026-07-27",
                value = 1,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        val callOrderLog = mutableListOf<String>()
        val dataSource = LocalHabitDataSourceImpl(
            habitDao = RecordingHabitDao(db.habitDao(), callOrderLog),
            habitCheckInDao = RecordingHabitCheckInDao(db.habitCheckInDao(), callOrderLog),
            transactionRunner = RoomTransactionRunner(db),
        )

        // Act
        val result = dataSource.deleteHabitAndCheckIns("habit-1", 2_000L)
        val habit = db.habitDao().getAllIncludingDeleted().single { it.id == "habit-1" }
        val checkIn = db.habitCheckInDao().getAllIncludingDeleted().single { it.id == "check-in-1" }
        db.close()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(listOf("check-in", "habit"), callOrderLog)
        assertEquals(2_000L, habit.deletedAt)
        assertEquals(2_000L, checkIn.deletedAt)
    }

    @Test
    fun `should roll back both deletes and leave nothing partially deleted when the habit soft-delete fails mid-transaction`() = runTest {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildDatabase(context)
        db.habitDao().insert(
            HabitEntity(
                id = "habit-1",
                name = "Beers",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        db.habitCheckInDao().insertOne(
            HabitCheckInEntity(
                id = "check-in-1",
                habitId = "habit-1",
                date = "2026-07-27",
                value = 1,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        val callOrderLog = mutableListOf<String>()
        val dataSource = LocalHabitDataSourceImpl(
            habitDao = RecordingHabitDao(db.habitDao(), callOrderLog, throwOnSoftDelete = true),
            habitCheckInDao = RecordingHabitCheckInDao(db.habitCheckInDao(), callOrderLog),
            transactionRunner = RoomTransactionRunner(db),
        )

        // Act
        val result = dataSource.deleteHabitAndCheckIns("habit-1", 2_000L)
        val habit = db.habitDao().getAllIncludingDeleted().single { it.id == "habit-1" }
        val checkIn = db.habitCheckInDao().getAllIncludingDeleted().single { it.id == "check-in-1" }
        db.close()

        // Assert: safeDbCall retries once, so both the attempt and its retry run the same
        // transaction, each rolling back cleanly on the same failure.
        assertTrue(result.isFailure)
        assertEquals(listOf("check-in", "habit", "check-in", "habit"), callOrderLog)
        assertNull(habit.deletedAt)
        assertNull(checkIn.deletedAt)
    }
}
