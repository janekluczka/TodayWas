package pl.luczka.todaywas.domain.sync.util

import pl.luczka.todaywas.domain.sync.model.LocalDataSummary

object LocalDataSyncPolicy {

    fun shouldReviewBeforeSync(
        hasSyncedLocalData: Boolean,
        summary: LocalDataSummary,
    ): Boolean = !hasSyncedLocalData && !summary.isEmpty
}
