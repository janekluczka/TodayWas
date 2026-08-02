package pl.luczka.todaywas.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitCheckInDaoTest {

    @Test
    fun `inserted check-in survives recreating the database instance from the same file`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"

            val db1 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            db1.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    habitId = 1L,
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )
            db1.close()

            val db2 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val persisted = db2.habitCheckInDao().observeAll().first()
            db2.close()

            assertEquals(1, persisted.size)
            assertEquals(1L, persisted[0].habitId)
            assertEquals("2026-07-27", persisted[0].date)
            assertEquals(1, persisted[0].value)
        }

    @Test
    fun `insertAll writes every entity in the batch`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"

            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            db.habitCheckInDao().insertAll(
                listOf(
                    HabitCheckInEntity(
                        habitId = 1L,
                        date = "2026-07-27",
                        value = 1,
                        createdAt = 1_000L,
                    ),
                    HabitCheckInEntity(
                        habitId = 2L,
                        date = "2026-07-27",
                        value = 3,
                        createdAt = 1_000L,
                    ),
                ),
            )

            val persisted = db.habitCheckInDao().observeAll().first()
            db.close()

            assertEquals(2, persisted.size)
        }

    @Test
    fun `insertAll is atomic - a batch containing a conflicting row commits nothing`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"

            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            // Pre-existing row that the batch's second entity will conflict with (same habitId+date).
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    habitId = 1L,
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )

            var threw = false
            try {
                db.habitCheckInDao().insertAll(
                    listOf(
                        // Would succeed in isolation - proves the transaction rolls this back too.
                        HabitCheckInEntity(
                            habitId = 2L,
                            date = "2026-07-27",
                            value = 3,
                            createdAt = 2_000L,
                        ),
                        // Conflicts with the pre-existing row on the unique (habitId, date) index.
                        HabitCheckInEntity(
                            habitId = 1L,
                            date = "2026-07-27",
                            value = 0,
                            createdAt = 2_000L,
                        ),
                    ),
                )
                fail("expected insertAll to throw on the conflicting row")
            } catch (e: Exception) {
                threw = true
            }

            val persisted = db.habitCheckInDao().observeAll().first()
            db.close()

            assertTrue(threw)
            assertEquals(1, persisted.size)
            assertEquals(1L, persisted[0].habitId)
            assertEquals(1_000L, persisted[0].createdAt)
        }

    @Test
    fun `getByHabitAndDate returns the matching entity or null`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"

            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    habitId = 1L,
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )

            val found = db.habitCheckInDao().getByHabitAndDate(1L, "2026-07-27")
            val missingDate = db.habitCheckInDao().getByHabitAndDate(1L, "2026-07-26")
            val missingHabit = db.habitCheckInDao().getByHabitAndDate(2L, "2026-07-27")
            db.close()

            assertEquals(1, found?.value)
            assertEquals(null, missingDate)
            assertEquals(null, missingHabit)
        }

    @Test
    fun `update persists new value and leaves habitId, date, and createdAt untouched`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"

            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    habitId = 1L,
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )
            val inserted = checkNotNull(db.habitCheckInDao().getByHabitAndDate(1L, "2026-07-27"))

            db.habitCheckInDao().update(inserted.copy(value = 0))
            val updated = db.habitCheckInDao().getByHabitAndDate(1L, "2026-07-27")
            db.close()

            assertEquals(0, updated?.value)
            assertEquals(1L, updated?.habitId)
            assertEquals("2026-07-27", updated?.date)
            assertEquals(1_000L, updated?.createdAt)
        }
}
