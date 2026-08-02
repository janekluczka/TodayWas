package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.core.designsystem.components.TodayWasContributionLevel
import pl.luczka.todaywas.domain.model.ContributionGrid
import pl.luczka.todaywas.domain.model.ContributionLevel
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.dateRange
import java.time.Instant
import java.time.LocalDate

data class ContributionGridUiState(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val cells: Map<LocalDate, TodayWasContributionLevel>,
)

sealed interface ContributionWindowUiState {

    data object RollingTwelveMonths : ContributionWindowUiState

    data class CalendarYear(
        val year: Int,
    ) : ContributionWindowUiState
}

fun ContributionGrid.toUiState(now: Instant): ContributionGridUiState {
    val range = window.dateRange(now)
    return ContributionGridUiState(
        startDate = range.start,
        endDate = range.endInclusive,
        cells = days.mapValues { (_, level) -> level.toUiState() },
    )
}

fun ContributionLevel.toUiState(): TodayWasContributionLevel = when (this) {
    ContributionLevel.NONE -> TodayWasContributionLevel.NONE
    ContributionLevel.LEVEL_1 -> TodayWasContributionLevel.LEVEL_1
    ContributionLevel.LEVEL_2 -> TodayWasContributionLevel.LEVEL_2
    ContributionLevel.LEVEL_3 -> TodayWasContributionLevel.LEVEL_3
    ContributionLevel.LEVEL_4 -> TodayWasContributionLevel.LEVEL_4
    ContributionLevel.LEVEL_5 -> TodayWasContributionLevel.LEVEL_5
}

fun ContributionWindow.toUiState(): ContributionWindowUiState = when (this) {
    ContributionWindow.RollingTwelveMonths -> ContributionWindowUiState.RollingTwelveMonths
    is ContributionWindow.CalendarYear -> ContributionWindowUiState.CalendarYear(year)
}

fun ContributionWindowUiState.toDomain(): ContributionWindow = when (this) {
    ContributionWindowUiState.RollingTwelveMonths -> ContributionWindow.RollingTwelveMonths
    is ContributionWindowUiState.CalendarYear -> ContributionWindow.CalendarYear(year)
}
