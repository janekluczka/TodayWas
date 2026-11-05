package pl.luczka.todaywas.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.luczka.todaywas.core.database.TodayWasDatabase
import pl.luczka.todaywas.core.database.dao.HabitCheckInDao
import pl.luczka.todaywas.core.database.dao.HabitDao
import pl.luczka.todaywas.core.database.dao.JournalEntryDao
import pl.luczka.todaywas.core.database.dao.UserPreferencesDao
import pl.luczka.todaywas.core.database.util.RoomTransactionRunner
import pl.luczka.todaywas.core.database.util.TransactionRunner
import pl.luczka.todaywas.core.database.withTodayWasSchemaHistory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TodayWasDatabase = Room
        .databaseBuilder(context, TodayWasDatabase::class.java, "todaywas.db")
        .withTodayWasSchemaHistory()
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
