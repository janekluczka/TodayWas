package pl.luczka.todaywas.ui.journal.list

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.JournalSortUiState

@Immutable
data class JournalListUiState(
    val isLoading: Boolean = true,
    val entries: List<JournalEntryUiState> = emptyList(),
    val selectedSort: JournalSortUiState = JournalSortUiState.NEWEST_FIRST,
    // The full multi-week, window-selectable grid.
    val contributionGrid: ContributionGridUiState = ContributionGridUiState(cells = emptyList()),
    val availableWindows: List<ContributionWindowUiState> =
        listOf(ContributionWindowUiState.RollingTwelveMonths),
    val selectedWindow: ContributionWindowUiState = ContributionWindowUiState.RollingTwelveMonths,
)
