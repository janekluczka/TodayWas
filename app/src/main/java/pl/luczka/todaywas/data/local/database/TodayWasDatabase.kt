package pl.luczka.todaywas.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import pl.luczka.todaywas.data.local.dao.HabitCheckInDao
import pl.luczka.todaywas.data.local.dao.HabitDao
import pl.luczka.todaywas.data.local.dao.JournalEntryDao
import pl.luczka.todaywas.data.local.dao.UserPreferencesDao
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.local.entity.UserPreferencesEntity

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
