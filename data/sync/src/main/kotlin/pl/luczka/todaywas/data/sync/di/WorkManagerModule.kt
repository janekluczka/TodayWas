package pl.luczka.todaywas.data.sync.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.luczka.todaywas.data.sync.util.WorkManagerSyncScheduler
import pl.luczka.todaywas.domain.entry.repository.SyncScheduler

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    fun provideSyncScheduler(scheduler: WorkManagerSyncScheduler): SyncScheduler = scheduler
}
