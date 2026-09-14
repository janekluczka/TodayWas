package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState

data class ContributionGridUiState(
    val cells: List<DsContributionCellUiState>,
)

// Two supported grid layouts, agnostic to the grid component itself (it only ever renders a
// precomputed cell list). BY_MONTH gives each month its own column block with truncated boundary
// columns (see `toByMonthCells`) so cells line up with actual calendar days — the only layout used
// by any screen today. CONTINUOUS (plain GitHub-style continuous weeks, no month awareness) is
// kept for the calculator/mapper tests that exercise both algorithms directly.
enum class ContributionGridType {
    CONTINUOUS,
    BY_MONTH,
}
