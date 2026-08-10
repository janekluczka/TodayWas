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
class TodayWasDatabaseTest {

    @Test
    fun `should survive recreating the database instance from the same file when a focus was saved`() =
        runTest {
            // Arrange
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "test-todaywas-${System.nanoTime()}.db"
            val db1 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()

            // Act
            db1.userPreferencesDao().upsert(
                UserPreferencesEntity(
                    focus = "JOURNAL",
                    onboardingCompleted = true,
                ),
            )
            db1.close()
            val db2 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val persisted = db2.userPreferencesDao().observe().first()
            db2.close()

            // Assert
            assertEquals("JOURNAL", persisted?.focus)
            assertEquals(true, persisted?.onboardingCompleted)
        }
}
