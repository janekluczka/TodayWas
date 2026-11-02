package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.model.LocalDataSummary
import pl.luczka.todaywas.domain.util.LocalDataSyncPolicy

class ShouldReviewLocalDataBeforeSyncUseCase @Inject constructor() {

    operator fun invoke(
        hasSyncedLocalData: Boolean,
        summary: LocalDataSummary,
    ): Boolean = LocalDataSyncPolicy.shouldReviewBeforeSync(hasSyncedLocalData, summary)
}
