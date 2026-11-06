package pl.luczka.todaywas.core.domain.usecase

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.core.domain.repository.JournalRepository
import pl.luczka.todaywas.core.domain.util.JournalContributionCalculator
import pl.luczka.todaywas.domain.entry.model.ContributionSummary
import pl.luczka.todaywas.domain.entry.model.ContributionWindow
import pl.luczka.todaywas.domain.entry.model.availableWindows

class ObserveJournalContributionUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val clock: Clock,
) {
    operator fun invoke(window: Flow<ContributionWindow>): Flow<ContributionSummary> = combine(
        repository.observeEntries(),
        window,
    ) { entries, w -> entries to w }
        .distinctUntilChanged()
        .map { (entries, w) ->
            val now = clock.instant()
            ContributionSummary(
                grid = JournalContributionCalculator.compute(entries, w, now),
                availableWindows = availableWindows(entries.minOfOrNull { it.date }, now),
            )
        }
}
