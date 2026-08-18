package pl.luczka.todaywas.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.data.local.dao.HabitCheckInDao
import pl.luczka.todaywas.data.local.dao.HabitDao
import pl.luczka.todaywas.data.local.dao.JournalEntryDao
import pl.luczka.todaywas.data.local.dao.UserPreferencesDao
import pl.luczka.todaywas.data.local.database.TodayWasDatabase
import pl.luczka.todaywas.data.local.database.todayWasDatabaseCallbacks
import pl.luczka.todaywas.data.util.RoomTransactionRunner
import pl.luczka.todaywas.data.util.TransactionRunner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TodayWasDatabase = Room
        .databaseBuilder(context, TodayWasDatabase::class.java, "todaywas.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(todayWasDatabaseCallbacks())
        .build()

    @Provides
    fun provideUserPreferencesDao(database: TodayWasDatabase): UserPreferencesDao = database.userPreferencesDao()

    @Provides
    fun provideJournalEntryDao(database: TodayWasDatabase): JournalEntryDao = database.journalEntryDao()

    @Provides
    fun provideHabitDao(database: TodayWasDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideHabitCheckInDao(database: TodayWasDatabase): HabitCheckInDao = database.habitCheckInDao()

    @Provides
    fun provideTransactionRunner(runner: RoomTransactionRunner): TransactionRunner = runner
}
