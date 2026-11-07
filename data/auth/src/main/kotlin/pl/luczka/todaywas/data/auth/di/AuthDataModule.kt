package pl.luczka.todaywas.data.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.luczka.todaywas.data.auth.repository.AuthRepositoryImpl
import pl.luczka.todaywas.domain.auth.repository.AuthRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
