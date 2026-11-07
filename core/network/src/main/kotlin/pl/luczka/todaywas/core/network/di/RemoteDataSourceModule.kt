package pl.luczka.todaywas.core.network.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.core.network.datasource.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.core.network.datasource.RemoteHabitCheckInDataSourceImpl
import pl.luczka.todaywas.core.network.datasource.RemoteHabitDataSource
import pl.luczka.todaywas.core.network.datasource.RemoteHabitDataSourceImpl
@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteDataSourceModule {

    @Binds
    abstract fun bindRemoteHabitDataSource(impl: RemoteHabitDataSourceImpl): RemoteHabitDataSource

    @Binds
    abstract fun bindRemoteHabitCheckInDataSource(impl: RemoteHabitCheckInDataSourceImpl): RemoteHabitCheckInDataSource
}
