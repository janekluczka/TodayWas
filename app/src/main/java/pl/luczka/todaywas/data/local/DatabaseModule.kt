package pl.luczka.todaywas.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TodayWasDatabase =
        Room
            .databaseBuilder(context, TodayWasDatabase::class.java, "todaywas.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideUserPreferencesDao(database: TodayWasDatabase): UserPreferencesDao = database.userPreferencesDao()

    @Provides
    fun provideJournalEntryDao(database: TodayWasDatabase): JournalEntryDao = database.journalEntryDao()
}
