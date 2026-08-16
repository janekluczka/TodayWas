package pl.luczka.todaywas.data.local.database

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
import pl.luczka.todaywas.data.local.entity.UserPreferencesEntity

@RunWith(RobolectricTestRunner::class)
class TodayWasDatabaseTest {

    @Test
    fun `should survive recreating the database instance from the same file when onboarding was completed`() =
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
            db1.userPreferencesDao().upsert(
                UserPreferencesEntity(onboardingCompleted = true),
            )
            db1.close()
            val db2 = Room
                .databaseBuilder(context, TodayWasDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
            val persisted = db2.userPreferencesDao().observe().first()
            db2.close()

            // Assert
            assertEquals(true, persisted?.onboardingCompleted)
        }
}
