package pl.luczka.todaywas.data.aiassist.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.data.aiassist.repository.AiAssistRepositoryImpl
import pl.luczka.todaywas.domain.aiassist.repository.AiAssistRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AiAssistDataModule {

    @Binds
    abstract fun bindAiAssistRepository(impl: AiAssistRepositoryImpl): AiAssistRepository
}
