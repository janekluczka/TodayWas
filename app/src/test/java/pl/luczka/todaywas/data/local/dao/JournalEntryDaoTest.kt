package pl.luczka.todaywas.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.luczka.todaywas.data.local.database.TodayWasDatabase
import pl.luczka.todaywas.data.local.database.todayWasDatabaseCallbacks
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class JournalEntryDaoTest {

    @Test
    fun `should survive recreating the database instance from the same file when an entry was inserted`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db1 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()

            // Act
            db1.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                ),
            )
            db1.close()
            val db2 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()
            val persisted = db2.journalEntryDao().observeAll().first()
            db2.close()

            // Assert
            assertEquals(1, persisted.size)
            assertEquals("2026-07-27", persisted[0].date)
            assertEquals("Today was good.", persisted[0].text)
            assertEquals(1_000L, persisted[0].createdAt)
        }

    @Test
    fun `should order entries by date descending when observeAll is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-25",
                    text = "Older",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-2",
                    date = "2026-07-27",
                    text = "Newest",
                    createdAt = 3L,
                    updatedAt = 3L,
                ),
            )
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-3",
                    date = "2026-07-26",
                    text = "Middle",
                    createdAt = 2L,
                    updatedAt = 2L,
                ),
            )

            // Act
            val entries = db.journalEntryDao().observeAll().first()
            db.close()

            // Assert
            assertEquals(listOf("2026-07-27", "2026-07-26", "2026-07-25"), entries.map { it.date })
        }

    @Test
    fun `should return the matching entry or null when getById is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                ),
            )
            val inserted = db
                .journalEntryDao()
                .observeAll()
                .first()
                .single()

            // Act
            val found = db.journalEntryDao().getById(inserted.id)
            val missing = db.journalEntryDao().getById(UUID.randomUUID().toString())
            db.close()

            // Assert
            assertEquals(inserted, found)
            assertEquals(null, missing)
        }

    @Test
    fun `should persist new text and leave date and createdAt untouched when update is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-27",
                    text = "Original text.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                ),
            )
            val inserted = db
                .journalEntryDao()
                .observeAll()
                .first()
                .single()

            // Act
            db.journalEntryDao().update(inserted.copy(text = "Edited text."))
            val updated = db.journalEntryDao().getById(inserted.id)
            db.close()

            // Assert
            assertEquals("Edited text.", updated?.text)
            assertEquals("2026-07-27", updated?.date)
            assertEquals(1_000L, updated?.createdAt)
        }

    @Test
    fun `should exclude only the matching entry from active reads when softDeleteById is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(id = "entry-1", date = "2026-07-27", text = "Keep me.", createdAt = 1_000L, updatedAt = 1_000L),
            )
            db.journalEntryDao().insert(
                JournalEntryEntity(id = "entry-2", date = "2026-07-26", text = "Delete me.", createdAt = 2_000L, updatedAt = 2_000L),
            )

            // Act
            db.journalEntryDao().softDeleteById("entry-2", 3_000L)
            val active = db.journalEntryDao().observeAll().first()
            val includingDeleted = db.journalEntryDao().getAllIncludingDeleted()
            db.close()

            // Assert
            assertEquals(listOf("entry-1"), active.map { it.id })
            assertEquals(setOf("entry-1", "entry-2"), includingDeleted.map { it.id }.toSet())
            assertEquals(3_000L, includingDeleted.single { it.id == "entry-2" }.deletedAt)
        }

    @Test
    fun `should reject a second active entry for the same date`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(id = "entry-1", date = "2026-07-27", text = "First.", createdAt = 1_000L, updatedAt = 1_000L),
            )

            // Act
            var threw = false
            try {
                db.journalEntryDao().insert(
                    JournalEntryEntity(id = "entry-2", date = "2026-07-27", text = "Second.", createdAt = 2_000L, updatedAt = 2_000L),
                )
                fail("expected insert to throw on the conflicting active date")
            } catch (e: Exception) {
                threw = true
            }
            db.close()

            // Assert
            assertTrue(threw)
        }

    @Test
    fun `should allow a fresh active entry after the previous one for the same date was soft-deleted`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(todayWasDatabaseCallbacks())
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(id = "entry-1", date = "2026-07-27", text = "First.", createdAt = 1_000L, updatedAt = 1_000L),
            )
            db.journalEntryDao().softDeleteById("entry-1", 2_000L)

            // Act
            db.journalEntryDao().insert(
                JournalEntryEntity(id = "entry-2", date = "2026-07-27", text = "Second.", createdAt = 3_000L, updatedAt = 3_000L),
            )
            val active = db.journalEntryDao().observeAll().first()
            db.close()

            // Assert
            assertEquals(listOf("entry-2"), active.map { it.id })
        }
}
