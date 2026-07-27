package pl.luczka.todaywas.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JournalEntryDaoTest {

    @Test
    fun `inserted entry survives recreating the database instance from the same file`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"

            val db1 =
                Room
                    .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                    .allowMainThreadQueries()
                    .build()
            db1.journalEntryDao().insert(
                JournalEntryEntity(date = "2026-07-27", text = "Today was good.", createdAt = 1_000L),
            )
            db1.close()

            val db2 =
                Room
                    .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                    .allowMainThreadQueries()
                    .build()
            val persisted = db2.journalEntryDao().observeAll().first()
            db2.close()

            assertEquals(1, persisted.size)
            assertEquals("2026-07-27", persisted[0].date)
            assertEquals("Today was good.", persisted[0].text)
            assertEquals(1_000L, persisted[0].createdAt)
        }

    @Test
    fun `observeAll orders entries by date descending`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"

            val db =
                Room
                    .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                    .allowMainThreadQueries()
                    .build()
            db.journalEntryDao().insert(JournalEntryEntity(date = "2026-07-25", text = "Older", createdAt = 1L))
            db.journalEntryDao().insert(JournalEntryEntity(date = "2026-07-27", text = "Newest", createdAt = 3L))
            db.journalEntryDao().insert(JournalEntryEntity(date = "2026-07-26", text = "Middle", createdAt = 2L))

            val entries = db.journalEntryDao().observeAll().first()
            db.close()

            assertEquals(listOf("2026-07-27", "2026-07-26", "2026-07-25"), entries.map { it.date })
        }
}
