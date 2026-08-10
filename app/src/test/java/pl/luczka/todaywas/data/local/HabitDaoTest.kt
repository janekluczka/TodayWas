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
class HabitDaoTest {

    @Test
    fun `should survive recreating the database instance from the same file when a habit was inserted`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db1 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()

            // Act
            db1.habitDao().insert(
                HabitEntity(
                    id = "habit-1",
                    name = "Drink water",
                    description = null,
                    type = "SCALE",
                    scaleMin = 1,
                    scaleMax = 5,
                    createdAt = 1_000L,
                ),
            )
            db1.close()
            val db2 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val persisted = db2.habitDao().observeAll().first()
            db2.close()

            // Assert
            assertEquals(1, persisted.size)
            assertEquals("Drink water", persisted[0].name)
            assertEquals("SCALE", persisted[0].type)
            assertEquals(1, persisted[0].scaleMin)
            assertEquals(5, persisted[0].scaleMax)
        }

    @Test
    fun `should order habits by createdAt ascending when observeAll is called`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            db.habitDao().insert(
                HabitEntity(
                    id = "habit-1",
                    name = "Newest",
                    description = null,
                    type = "BINARY",
                    scaleMin = null,
                    scaleMax = null,
                    createdAt = 3L,
                ),
            )
            db.habitDao().insert(
                HabitEntity(
                    id = "habit-2",
                    name = "Oldest",
                    description = null,
                    type = "BINARY",
                    scaleMin = null,
                    scaleMax = null,
                    createdAt = 1L,
                ),
            )
            db.habitDao().insert(
                HabitEntity(
                    id = "habit-3",
                    name = "Middle",
                    description = null,
                    type = "BINARY",
                    scaleMin = null,
                    scaleMax = null,
                    createdAt = 2L,
                ),
            )

            // Act
            val habits = db.habitDao().observeAll().first()
            db.close()

            // Assert
            assertEquals(listOf("Oldest", "Middle", "Newest"), habits.map { it.name })
        }
}
