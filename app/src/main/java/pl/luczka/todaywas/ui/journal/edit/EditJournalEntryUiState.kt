package pl.luczka.todaywas.ui.journal.edit

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.journal.create.HelpMeStartUiState
import pl.luczka.todaywas.ui.journal.create.JournalStarterPromptUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Immutable
data class EditJournalEntryUiState(
    val isLoading: Boolean,
    val entry: JournalEntryUiState?,
    val text: String,
    val isSaving: Boolean,
    val saveError: Boolean,
    val isDiscardConfirmVisible: Boolean = false,
    val authState: AuthStateUi = AuthStateUi.Loading,
    val helpMeStart: HelpMeStartUiState = HelpMeStartUiState(),
    val helpMeRefine: HelpMeRefineUiState = HelpMeRefineUiState(),
    // One entry per JournalStarterPromptTone, same randomly-picked-once-per-VM variant scheme as
    // Add Journal Entry -- see EditJournalEntryViewModel.
    val starterPrompts: List<JournalStarterPromptUiState> = emptyList(),
)
