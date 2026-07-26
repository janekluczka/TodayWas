package pl.luczka.todaywas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UserPreferencesEntity::class], version = 1)
abstract class TodayWasDatabase : RoomDatabase() {
    abstract fun userPreferencesDao(): UserPreferencesDao
}
