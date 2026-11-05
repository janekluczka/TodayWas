package pl.luczka.todaywas.core.domain.usecase

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.core.domain.model.ContributionSummary
import pl.luczka.todaywas.core.domain.model.ContributionWindow
import pl.luczka.todaywas.core.domain.model.availableWindows
import pl.luczka.todaywas.core.domain.repository.HabitRepository
import pl.luczka.todaywas.core.domain.util.HabitContributionCalculator

class ObserveHabitContributionUseCase @Inject constructor(
    private val repository: HabitRepository,
    private val clock: Clock,
) {
    operator fun invoke(
        habitId: String,
        window: Flow<ContributionWindow>,
    ): Flow<ContributionSummary> = combine(repository.observeCheckIns(habitId), window) { c, w ->
        c to w
    }.distinctUntilChanged()
        .map { (c, w) ->
            val now = clock.instant()
            ContributionSummary(
                grid = HabitContributionCalculator.compute(c, w, now),
                availableWindows = availableWindows(c.minOfOrNull { it.date }, now),
            )
        }
}
