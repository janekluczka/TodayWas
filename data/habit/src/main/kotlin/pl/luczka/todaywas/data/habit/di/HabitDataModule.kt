package pl.luczka.todaywas.data.habit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.luczka.todaywas.data.habit.remote.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.habit.remote.RemoteHabitCheckInDataSourceImpl
import pl.luczka.todaywas.data.habit.remote.RemoteHabitDataSource
import pl.luczka.todaywas.data.habit.remote.RemoteHabitDataSourceImpl
import pl.luczka.todaywas.data.habit.repository.HabitRepositoryImpl
import pl.luczka.todaywas.domain.habit.repository.HabitRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class HabitDataModule {

    @Binds
    @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    abstract fun bindRemoteHabitDataSource(impl: RemoteHabitDataSourceImpl): RemoteHabitDataSource

    @Binds
    abstract fun bindRemoteHabitCheckInDataSource(impl: RemoteHabitCheckInDataSourceImpl): RemoteHabitCheckInDataSource
}
