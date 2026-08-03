package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.core.designsystem.components.TodayWasContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.TodayWasContributionLevel
import pl.luczka.todaywas.domain.model.ContributionGrid
import pl.luczka.todaywas.domain.model.ContributionLevel
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.dateRange
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

data class ContributionGridUiState(
    val cells: List<TodayWasContributionCellUiState>,
)

sealed interface ContributionWindowUiState {

    data object RollingTwelveMonths : ContributionWindowUiState

    data class CalendarYear(
        val year: Int,
    ) : ContributionWindowUiState
}

// Two supported grid layouts, so a future user-facing preference can switch between them without
// touching the grid component itself (it only ever renders a precomputed cell list, agnostic to
// which layout produced it). CONTINUOUS is the default — plain GitHub-style continuous weeks, no
// month awareness. BY_MONTH gives each month its own column block with truncated boundary columns
// (see `toByMonthCells`) but isn't wired into any screen yet.
enum class ContributionGridType {
    CONTINUOUS,
    BY_MONTH,
}

fun ContributionGrid.toUiState(
    now: Instant,
    type: ContributionGridType = ContributionGridType.CONTINUOUS,
): ContributionGridUiState {
    val range = window.dateRange(now)
    val cells = when (type) {
        ContributionGridType.CONTINUOUS -> toContinuousCells(range.start, range.endInclusive)
        ContributionGridType.BY_MONTH -> toByMonthCells(range.start, range.endInclusive)
    }
    // Reverses the whole flat list (paired with the grid's reverseLayout = true) so the most
    // recent week still anchors the visual end with no blank trailing space — every column here is
    // uniformly 7 items, so this rides through the reversal cleanly alongside the date cells.
    return ContributionGridUiState(cells = cells.reversed())
}

// Plain continuous weeks, padded to whole Monday..Sunday blocks across the entire window — no
// month awareness, matching the literal GitHub contribution graph shape.
private fun ContributionGrid.toContinuousCells(
    startDate: LocalDate,
    endDate: LocalDate,
): List<TodayWasContributionCellUiState> {
    val paddedStart = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
    val paddedEnd = endDate.plusDays(((7 - endDate.dayOfWeek.value) % 7).toLong())
    return generateSequence(paddedStart) { it.plusDays(1) }
        .takeWhile { it <= paddedEnd }
        .map { date ->
            val level = days[date]?.toUiState() ?: TodayWasContributionLevel.NONE
            TodayWasContributionCellUiState.Level(date, level)
        }.toList()
}

// Months own their own contiguous block of columns rather than one continuously-padded window:
// each column is built from the current position through the end of its calendar week, the
// window, OR the current month — whichever comes first. This means a month boundary that falls
// mid-week splits that calendar week into two side-by-side partial columns (one per month) instead
// of one shared week showing days from both months, and the window's own start/end similarly
// truncate the very first/last column instead of spilling into out-of-window placeholder days.
private fun ContributionGrid.toByMonthCells(
    startDate: LocalDate,
    endDate: LocalDate,
): List<TodayWasContributionCellUiState> {
    val cells = mutableListOf<TodayWasContributionCellUiState>()
    var currentDate = startDate
    var previousMonth: YearMonth? = null
    while (currentDate <= endDate) {
        val weekMonday = currentDate.minusDays((currentDate.dayOfWeek.value - 1).toLong())
        val weekSunday = weekMonday.plusDays(6)
        val monthEnd = YearMonth.from(currentDate).atEndOfMonth()
        val columnEnd = minOf(weekSunday, endDate, monthEnd)

        val columnMonth = YearMonth.from(currentDate)
        val hasGapBefore = previousMonth != null && columnMonth != previousMonth
        previousMonth = columnMonth

        (0..6).forEach { rowOffset ->
            val rowDate = weekMonday.plusDays(rowOffset.toLong())
            cells.add(
                if (rowDate < currentDate || rowDate > columnEnd) {
                    TodayWasContributionCellUiState.Blank(hasGapBefore)
                } else {
                    val level = days[rowDate]?.toUiState() ?: TodayWasContributionLevel.NONE
                    TodayWasContributionCellUiState.Level(rowDate, level, hasGapBefore)
                },
            )
        }
        currentDate = columnEnd.plusDays(1)
    }
    return cells
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
