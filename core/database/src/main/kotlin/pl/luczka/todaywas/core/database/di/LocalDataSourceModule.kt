package pl.luczka.todaywas.core.database.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.core.database.datasource.LocalHabitDataSource
import pl.luczka.todaywas.core.database.datasource.LocalHabitDataSourceImpl
import pl.luczka.todaywas.core.database.datasource.LocalJournalDataSource
import pl.luczka.todaywas.core.database.datasource.LocalJournalDataSourceImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalDataSourceModule {

    @Binds
    abstract fun bindLocalHabitDataSource(impl: LocalHabitDataSourceImpl): LocalHabitDataSource

    @Binds
    abstract fun bindLocalJournalDataSource(impl: LocalJournalDataSourceImpl): LocalJournalDataSource
}
