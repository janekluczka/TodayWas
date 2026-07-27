package pl.luczka.todaywas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UserPreferencesEntity::class, JournalEntryEntity::class], version = 2, exportSchema = false)
abstract class TodayWasDatabase : RoomDatabase() {

    abstract fun userPreferencesDao(): UserPreferencesDao

    abstract fun journalEntryDao(): JournalEntryDao
}
