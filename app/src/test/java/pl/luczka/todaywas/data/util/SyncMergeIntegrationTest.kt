package pl.luczka.todaywas.data.util

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.luczka.todaywas.data.local.database.TodayWasDatabase
import pl.luczka.todaywas.data.local.database.todayWasDatabaseCallbacks
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.mapper.toSyncMeta

// End-to-end regression coverage for the soft-delete -> sync-push chain: unlike SyncMergeTest.kt
// (mergeForSync() against hand-built SyncMeta) or the DAO tests (deletedAt gets set, active reads
// filter it out), these chain a real Room DB soft-delete through the real toSyncMeta() mapper into
// the real mergeForSync() decision, for all three entity types.
@RunWith(RobolectricTestRunner::class)
class SyncMergeIntegrationTest {

    private fun buildDatabase(context: Context): TodayWasDatabase = Room
        .databaseBuilder(context, TodayWasDatabase::class.java, "test-todaywas-${System.nanoTime()}.db")
        .allowMainThreadQueries()
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .addCallback(todayWasDatabaseCallbacks())
        .build()

    @Test
    fun `should select a soft-deleted journal entry for push when remote never saw it`() = runTest {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildDatabase(context)
        db.journalEntryDao().insert(
            JournalEntryEntity(
                id = "entry-1",
                date = "2026-07-27",
                text = "text",
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

        // Act
        db.journalEntryDao().softDeleteById("entry-1", 2_000L)
        val local = db.journalEntryDao().getAllIncludingDeleted().toSyncMeta()
        val decision = mergeForSync(local, remote = emptyList())
        db.close()

        // Assert
        assertEquals(setOf("entry-1"), decision.pushIds)
        assertEquals(emptySet<String>(), decision.applyIds)
    }

    @Test
    fun `should select a soft-deleted habit for push when remote never saw it`() = runTest {
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

        // Act
        db.habitDao().softDeleteById("habit-1", 2_000L)
        val local = db.habitDao().getAllIncludingDeleted().toSyncMeta()
        val decision = mergeForSync(local, remote = emptyList())
        db.close()

        // Assert
        assertEquals(setOf("habit-1"), decision.pushIds)
        assertEquals(emptySet<String>(), decision.applyIds)
    }

    @Test
    fun `should select a soft-deleted habit check-in for push when remote never saw it`() = runTest {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildDatabase(context)
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

        // Act
        db.habitCheckInDao().softDeleteById("check-in-1", 2_000L)
        val local = db.habitCheckInDao().getAllIncludingDeleted().toSyncMeta()
        val decision = mergeForSync(local, remote = emptyList())
        db.close()

        // Assert
        assertEquals(setOf("check-in-1"), decision.pushIds)
        assertEquals(emptySet<String>(), decision.applyIds)
    }
}
