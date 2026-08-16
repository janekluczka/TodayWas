package pl.luczka.todaywas.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSourceImpl
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSourceImpl
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSource
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSourceImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository

    @Binds
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindAiAssistRepository(impl: AiAssistRepositoryImpl): AiAssistRepository

    @Binds
    abstract fun bindRemoteJournalDataSource(impl: RemoteJournalDataSourceImpl): RemoteJournalDataSource

    @Binds
    abstract fun bindRemoteHabitDataSource(impl: RemoteHabitDataSourceImpl): RemoteHabitDataSource

    @Binds
    abstract fun bindRemoteHabitCheckInDataSource(impl: RemoteHabitCheckInDataSourceImpl): RemoteHabitCheckInDataSource
}
