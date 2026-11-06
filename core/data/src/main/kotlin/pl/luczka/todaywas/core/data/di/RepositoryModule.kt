package pl.luczka.todaywas.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.luczka.todaywas.core.data.repository.AiAssistRepositoryImpl
import pl.luczka.todaywas.core.data.repository.AuthRepositoryImpl
import pl.luczka.todaywas.core.data.repository.HabitRepositoryImpl
import pl.luczka.todaywas.core.data.repository.JournalRepositoryImpl
import pl.luczka.todaywas.core.data.repository.OnboardingRepositoryImpl
import pl.luczka.todaywas.core.domain.repository.AiAssistRepository
import pl.luczka.todaywas.core.domain.repository.HabitRepository
import pl.luczka.todaywas.core.domain.repository.JournalRepository
import pl.luczka.todaywas.core.domain.repository.OnboardingRepository
import pl.luczka.todaywas.domain.auth.repository.AuthRepository

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
}
