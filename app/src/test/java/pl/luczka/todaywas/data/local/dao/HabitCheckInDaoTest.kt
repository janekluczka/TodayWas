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
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity

@RunWith(RobolectricTestRunner::class)
class HabitCheckInDaoTest {

    @Test
    fun `should survive recreating the database instance from the same file when a check-in was inserted`() =
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
            db1.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    id = "check-in-1",
                    habitId = "1",
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )
            db1.close()
            val db2 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            val persisted = db2.habitCheckInDao().observeAll().first()
            db2.close()

            // Assert
            assertEquals(1, persisted.size)
            assertEquals("1", persisted[0].habitId)
            assertEquals("2026-07-27", persisted[0].date)
            assertEquals(1, persisted[0].value)
        }

    @Test
    fun `should write every entity in the batch when insertAll is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()

            // Act
            db.habitCheckInDao().insertAll(
                listOf(
                    HabitCheckInEntity(
                        id = "check-in-1",
                        habitId = "1",
                        date = "2026-07-27",
                        value = 1,
                        createdAt = 1_000L,
                    ),
                    HabitCheckInEntity(
                        id = "check-in-2",
                        habitId = "2",
                        date = "2026-07-27",
                        value = 3,
                        createdAt = 1_000L,
                    ),
                ),
            )
            val persisted = db.habitCheckInDao().observeAll().first()
            db.close()

            // Assert
            assertEquals(2, persisted.size)
        }

    @Test
    fun `should commit nothing when insertAll's batch contains a conflicting row`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            // Pre-existing row that the batch's second entity will conflict with (same habitId+date).
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    id = "check-in-1",
                    habitId = "1",
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )

            // Act
            var threw = false
            try {
                db.habitCheckInDao().insertAll(
                    listOf(
                        // Would succeed in isolation - proves the transaction rolls this back too.
                        HabitCheckInEntity(
                            id = "check-in-2",
                            habitId = "2",
                            date = "2026-07-27",
                            value = 3,
                            createdAt = 2_000L,
                        ),
                        // Conflicts with the pre-existing row on the unique (habitId, date) index.
                        HabitCheckInEntity(
                            id = "check-in-3",
                            habitId = "1",
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

            // Assert
            assertTrue(threw)
            assertEquals(1, persisted.size)
            assertEquals("1", persisted[0].habitId)
            assertEquals(1_000L, persisted[0].createdAt)
        }

    @Test
    fun `should return the matching entity or null when getByHabitAndDate is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    id = "check-in-1",
                    habitId = "1",
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )

            // Act
            val found = db.habitCheckInDao().getByHabitAndDate("1", "2026-07-27")
            val missingDate = db.habitCheckInDao().getByHabitAndDate("1", "2026-07-26")
            val missingHabit = db.habitCheckInDao().getByHabitAndDate("2", "2026-07-27")
            db.close()

            // Assert
            assertEquals(1, found?.value)
            assertEquals(null, missingDate)
            assertEquals(null, missingHabit)
        }

    @Test
    fun `should persist new value and leave habitId, date, and createdAt untouched when update is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(
                    id = "check-in-1",
                    habitId = "1",
                    date = "2026-07-27",
                    value = 1,
                    createdAt = 1_000L,
                ),
            )
            val inserted = checkNotNull(db.habitCheckInDao().getByHabitAndDate("1", "2026-07-27"))

            // Act
            db.habitCheckInDao().update(inserted.copy(value = 0))
            val updated = db.habitCheckInDao().getByHabitAndDate("1", "2026-07-27")
            db.close()

            // Assert
            assertEquals(0, updated?.value)
            assertEquals("1", updated?.habitId)
            assertEquals("2026-07-27", updated?.date)
            assertEquals(1_000L, updated?.createdAt)
        }

    @Test
    fun `should remove only the matching check-in when deleteById is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L),
            )
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(id = "check-in-2", habitId = "1", date = "2026-07-26", value = 0, createdAt = 2_000L),
            )

            // Act
            db.habitCheckInDao().deleteById("check-in-2")
            val remaining = db.habitCheckInDao().observeAll().first()
            db.close()

            // Assert
            assertEquals(listOf("check-in-1"), remaining.map { it.id })
        }

    @Test
    fun `should remove every check-in for the habit when deleteByHabitId is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L),
            )
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(id = "check-in-2", habitId = "1", date = "2026-07-26", value = 0, createdAt = 2_000L),
            )
            db.habitCheckInDao().insertOne(
                HabitCheckInEntity(id = "check-in-3", habitId = "2", date = "2026-07-27", value = 1, createdAt = 3_000L),
            )

            // Act
            db.habitCheckInDao().deleteByHabitId("1")
            val remaining = db.habitCheckInDao().observeAll().first()
            db.close()

            // Assert
            assertEquals(listOf("check-in-3"), remaining.map { it.id })
        }
}
