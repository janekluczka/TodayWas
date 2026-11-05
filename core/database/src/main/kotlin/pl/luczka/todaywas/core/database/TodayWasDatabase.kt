package pl.luczka.todaywas.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import pl.luczka.todaywas.core.database.dao.HabitCheckInDao
import pl.luczka.todaywas.core.database.dao.HabitDao
import pl.luczka.todaywas.core.database.dao.JournalEntryDao
import pl.luczka.todaywas.core.database.dao.UserPreferencesDao
import pl.luczka.todaywas.core.database.entity.HabitCheckInEntity
import pl.luczka.todaywas.core.database.entity.HabitEntity
import pl.luczka.todaywas.core.database.entity.JournalEntryEntity
import pl.luczka.todaywas.core.database.entity.UserPreferencesEntity

// Bumping this needs a migration in TodayWasMigrations.kt and the newly exported schemas/<version>.json.
const val TODAY_WAS_DATABASE_VERSION = 6

@Database(
    entities = [UserPreferencesEntity::class, JournalEntryEntity::class, HabitEntity::class, HabitCheckInEntity::class],
    version = TODAY_WAS_DATABASE_VERSION,
    exportSchema = true,
)
abstract class TodayWasDatabase : RoomDatabase() {

    abstract fun userPreferencesDao(): UserPreferencesDao

    abstract fun journalEntryDao(): JournalEntryDao

    abstract fun habitDao(): HabitDao

    abstract fun habitCheckInDao(): HabitCheckInDao
}
