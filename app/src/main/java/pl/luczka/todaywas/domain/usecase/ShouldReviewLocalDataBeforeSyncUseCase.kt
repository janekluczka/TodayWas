package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.model.LocalDataSummary
import pl.luczka.todaywas.domain.util.LocalDataSyncPolicy
import javax.inject.Inject

class ShouldReviewLocalDataBeforeSyncUseCase @Inject constructor() {

    operator fun invoke(
        hasSyncedLocalData: Boolean,
        summary: LocalDataSummary,
    ): Boolean = LocalDataSyncPolicy.shouldReviewBeforeSync(hasSyncedLocalData, summary)
}
