package pl.luczka.todaywas.domain.sync.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.sync.model.LocalDataSummary
import pl.luczka.todaywas.domain.sync.util.LocalDataSyncPolicy

class ShouldReviewLocalDataBeforeSyncUseCase @Inject constructor() {

    operator fun invoke(
        hasSyncedLocalData: Boolean,
        summary: LocalDataSummary,
    ): Boolean = LocalDataSyncPolicy.shouldReviewBeforeSync(hasSyncedLocalData, summary)
}
