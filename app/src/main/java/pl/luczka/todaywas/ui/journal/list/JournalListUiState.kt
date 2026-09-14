package pl.luczka.todaywas.ui.journal.list

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.JournalSortUiState

@Immutable
data class JournalListUiState(
    val isLoading: Boolean = true,
    val entries: List<JournalEntryUiState> = emptyList(),
    val selectedSort: JournalSortUiState = JournalSortUiState.NEWEST_FIRST,
    // The full multi-week, month-aligned grid, always the rolling 12-month window.
    val contributionGrid: ContributionGridUiState = ContributionGridUiState(cells = emptyList()),
)
