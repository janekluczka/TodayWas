package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.domain.model.ContributionGrid
import pl.luczka.todaywas.domain.model.ContributionLevel
import pl.luczka.todaywas.domain.model.dateRange
import pl.luczka.todaywas.ui.model.ContributionGridType
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

fun ContributionGrid.toUiState(
    now: Instant,
    type: ContributionGridType = ContributionGridType.BY_MONTH,
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
): List<DsContributionCellUiState> {
    val paddedStart = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
    val paddedEnd = endDate.plusDays(((7 - endDate.dayOfWeek.value) % 7).toLong())
    return generateSequence(paddedStart) { it.plusDays(1) }
        .takeWhile { it <= paddedEnd }
        .map { date ->
            val level = days[date]?.toUiState() ?: DsContributionLevel.NONE
            DsContributionCellUiState.Level(date, level)
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
): List<DsContributionCellUiState> {
    val cells = mutableListOf<DsContributionCellUiState>()
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
                    DsContributionCellUiState.Blank(hasGapBefore)
                } else {
                    val level = days[rowDate]?.toUiState() ?: DsContributionLevel.NONE
                    DsContributionCellUiState.Level(rowDate, level, hasGapBefore)
                },
            )
        }
        currentDate = columnEnd.plusDays(1)
    }
    return cells
}

fun ContributionLevel.toUiState(): DsContributionLevel = when (this) {
    ContributionLevel.NONE -> DsContributionLevel.NONE
    ContributionLevel.LEVEL_1 -> DsContributionLevel.LEVEL_1
    ContributionLevel.LEVEL_2 -> DsContributionLevel.LEVEL_2
    ContributionLevel.LEVEL_3 -> DsContributionLevel.LEVEL_3
    ContributionLevel.LEVEL_4 -> DsContributionLevel.LEVEL_4
    ContributionLevel.LEVEL_5 -> DsContributionLevel.LEVEL_5
}
