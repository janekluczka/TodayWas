package pl.luczka.todaywas.data.journal.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.luczka.todaywas.data.journal.remote.RemoteJournalDataSource
import pl.luczka.todaywas.data.journal.remote.RemoteJournalDataSourceImpl
import pl.luczka.todaywas.data.journal.repository.JournalRepositoryImpl
import pl.luczka.todaywas.domain.journal.repository.JournalRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class JournalDataModule {

    @Binds
    @Singleton
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository

    @Binds
    abstract fun bindRemoteJournalDataSource(impl: RemoteJournalDataSourceImpl): RemoteJournalDataSource
}
