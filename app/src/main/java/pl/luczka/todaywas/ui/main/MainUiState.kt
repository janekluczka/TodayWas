package pl.luczka.todaywas.ui.main

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Immutable
data class MainUiState(
    val focus: FocusUiState?,
    val journalEntries: List<JournalEntryUiState>,
    val habits: List<HabitUiState>,
    val journalContributionGrid: ContributionGridUiState,
    val journalAvailableWindows: List<ContributionWindowUiState>,
    val journalSelectedWindow: ContributionWindowUiState,
    val fabActions: List<FabActionUiState>,
    val fabExpanded: Boolean,
)
