package pl.luczka.todaywas.data.journal.mapper

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.luczka.todaywas.core.database.TodayWasDatabase
import pl.luczka.todaywas.core.database.entity.JournalEntryEntity
import pl.luczka.todaywas.core.database.todayWasDatabaseCallbacks
import pl.luczka.todaywas.data.journal.mapper.toSyncMeta
import pl.luczka.todaywas.domain.entry.util.mergeForSync

// End-to-end regression coverage for the soft-delete -> sync-push chain: unlike SyncMergeTest.kt
// (mergeForSync() against hand-built SyncMeta) or the DAO tests (deletedAt gets set, active reads
// filter it out), these chain a real Room DB soft-delete through the real toSyncMeta() mapper into
// the real mergeForSync() decision, for journal entries (HabitSyncMergeIntegrationTest covers habits).
@RunWith(RobolectricTestRunner::class)
class JournalSyncMergeIntegrationTest {

    private fun buildDatabase(context: Context): TodayWasDatabase =
        Room
            .databaseBuilder(
                context,
                TodayWasDatabase::class.java,
                "test-todaywas-${System.nanoTime()}.db",
            ).allowMainThreadQueries()
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addCallback(todayWasDatabaseCallbacks())
            .build()

    @Test
    fun `should select a soft-deleted journal entry for push when remote never saw it`() =
        runTest {
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
            val decision = mergeForSync(
                local,
                remote = emptyList(),
            )
            db.close()

            // Assert
            assertEquals(setOf("entry-1"), decision.pushIds)
            assertEquals(emptySet<String>(), decision.applyIds)
        }
}
