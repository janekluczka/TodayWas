package pl.luczka.todaywas.domain.util

import pl.luczka.todaywas.domain.model.LocalDataSummary

object LocalDataSyncPolicy {

    fun shouldReviewBeforeSync(
        hasSyncedLocalData: Boolean,
        summary: LocalDataSummary,
    ): Boolean = !hasSyncedLocalData && !summary.isEmpty
}
