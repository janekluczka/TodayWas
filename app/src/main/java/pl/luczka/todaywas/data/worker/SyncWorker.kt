package pl.luczka.todaywas.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncLocalData: SyncLocalDataUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (syncLocalData().isSuccess) Result.success() else Result.retry()
}
