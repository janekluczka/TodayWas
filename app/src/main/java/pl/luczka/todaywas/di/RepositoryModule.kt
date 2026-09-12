package pl.luczka.todaywas.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.data.local.api.LocalHabitDataSource
import pl.luczka.todaywas.data.local.api.LocalHabitDataSourceImpl
import pl.luczka.todaywas.data.local.api.LocalJournalDataSource
import pl.luczka.todaywas.data.local.api.LocalJournalDataSourceImpl
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSourceImpl
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSourceImpl
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSource
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSourceImpl
import pl.luczka.todaywas.data.repository.AiAssistRepositoryImpl
import pl.luczka.todaywas.data.repository.AuthRepositoryImpl
import pl.luczka.todaywas.data.repository.HabitRepositoryImpl
import pl.luczka.todaywas.data.repository.JournalRepositoryImpl
import pl.luczka.todaywas.data.repository.OnboardingRepositoryImpl
import pl.luczka.todaywas.domain.repository.AiAssistRepository
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.HabitRepository
import pl.luczka.todaywas.domain.repository.JournalRepository
import pl.luczka.todaywas.domain.repository.OnboardingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository

    @Binds
    @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindAiAssistRepository(impl: AiAssistRepositoryImpl): AiAssistRepository

    @Binds
    abstract fun bindRemoteJournalDataSource(
        impl: RemoteJournalDataSourceImpl,
    ): RemoteJournalDataSource

    @Binds
    abstract fun bindRemoteHabitDataSource(impl: RemoteHabitDataSourceImpl): RemoteHabitDataSource

    @Binds
    abstract fun bindRemoteHabitCheckInDataSource(
        impl: RemoteHabitCheckInDataSourceImpl,
    ): RemoteHabitCheckInDataSource

    @Binds
    abstract fun bindLocalHabitDataSource(impl: LocalHabitDataSourceImpl): LocalHabitDataSource

    @Binds
    abstract fun bindLocalJournalDataSource(
        impl: LocalJournalDataSourceImpl,
    ): LocalJournalDataSource
}
