package pl.luczka.todaywas.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.luczka.todaywas.data.local.database.TodayWasDatabase
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
                .build()

            // Act
            db1.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                ),
            )
            db1.close()
            val db2 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
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
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-25",
                    text = "Older",
                    createdAt = 1L,
                ),
            )
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-2",
                    date = "2026-07-27",
                    text = "Newest",
                    createdAt = 3L,
                ),
            )
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-3",
                    date = "2026-07-26",
                    text = "Middle",
                    createdAt = 2L,
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
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
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
                .build()
            db.journalEntryDao().insert(
                JournalEntryEntity(
                    id = "entry-1",
                    date = "2026-07-27",
                    text = "Original text.",
                    createdAt = 1_000L,
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
}
