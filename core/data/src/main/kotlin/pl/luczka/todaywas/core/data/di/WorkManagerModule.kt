package pl.luczka.todaywas.core.data.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.luczka.todaywas.core.data.util.SyncScheduler
import pl.luczka.todaywas.core.data.util.WorkManagerSyncScheduler

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    fun provideSyncScheduler(scheduler: WorkManagerSyncScheduler): SyncScheduler = scheduler
}
