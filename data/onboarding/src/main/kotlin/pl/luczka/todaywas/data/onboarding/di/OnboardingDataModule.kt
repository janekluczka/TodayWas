package pl.luczka.todaywas.data.onboarding.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.data.onboarding.repository.OnboardingRepositoryImpl
import pl.luczka.todaywas.domain.onboarding.repository.OnboardingRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingDataModule {

    @Binds
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository
}
