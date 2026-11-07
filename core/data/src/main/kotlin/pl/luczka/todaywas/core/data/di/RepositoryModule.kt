package pl.luczka.todaywas.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.luczka.todaywas.core.data.repository.HabitRepositoryImpl
import pl.luczka.todaywas.core.data.repository.JournalRepositoryImpl
import pl.luczka.todaywas.domain.habit.repository.HabitRepository
import pl.luczka.todaywas.domain.journal.repository.JournalRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository

    @Binds
    @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository
}
