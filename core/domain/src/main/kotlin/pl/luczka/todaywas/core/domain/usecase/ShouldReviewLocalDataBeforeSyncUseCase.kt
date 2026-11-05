package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.core.domain.model.LocalDataSummary
import pl.luczka.todaywas.core.domain.util.LocalDataSyncPolicy

class ShouldReviewLocalDataBeforeSyncUseCase @Inject constructor() {

    operator fun invoke(
        hasSyncedLocalData: Boolean,
        summary: LocalDataSummary,
    ): Boolean = LocalDataSyncPolicy.shouldReviewBeforeSync(hasSyncedLocalData, summary)
}
