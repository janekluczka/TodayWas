package pl.luczka.todaywas.core.domain.util

import pl.luczka.todaywas.core.domain.model.LocalDataSummary

object LocalDataSyncPolicy {

    fun shouldReviewBeforeSync(
        hasSyncedLocalData: Boolean,
        summary: LocalDataSummary,
    ): Boolean = !hasSyncedLocalData && !summary.isEmpty
}
