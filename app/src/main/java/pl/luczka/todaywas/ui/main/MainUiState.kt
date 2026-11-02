package pl.luczka.todaywas.ui.main

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Immutable
data class MainUiState(
    val isLoading: Boolean,
    val journalEntries: List<JournalEntryUiState>,
    val habits: List<HabitUiState>,
    val disabledFabActions: Set<FabActionUiState>,
    val fabExpanded: Boolean,
    val authState: AuthStateUi,
    val isAccountSheetVisible: Boolean,
    val isSignOutConfirmVisible: Boolean,
    val isSigningOut: Boolean,
    // An error to show once in a snackbar; cleared by MainIntent.UserMessageShown.
    val userMessage: AuthErrorUiState? = null,
)
