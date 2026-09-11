package pl.luczka.todaywas.ui.main

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Immutable
data class MainUiState(
    val isLoading: Boolean,
    val journalEntries: List<JournalEntryUiState>,
    val habits: List<HabitUiState>,
    // The same full RollingTwelveMonths cell list the Journal list screen's grid uses, rendered
    // as one scrollable row (DsContributionRow) instead of stacked weekly columns — a quick-glance
    // strip, not a fixed/capped window. Window selection lives on the Journal list screen.
    val journalContributionCells: List<DsContributionCellUiState>,
    val fabActions: List<FabActionUiState>,
    val fabExpanded: Boolean,
    val authState: AuthStateUi,
    val isAccountSheetVisible: Boolean,
    val isSignOutConfirmVisible: Boolean,
    val isSigningOut: Boolean,
)
