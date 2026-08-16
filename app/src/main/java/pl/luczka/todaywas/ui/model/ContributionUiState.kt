package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState

data class ContributionGridUiState(
    val cells: List<DsContributionCellUiState>,
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
