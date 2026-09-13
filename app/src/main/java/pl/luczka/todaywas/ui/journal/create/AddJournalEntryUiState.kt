package pl.luczka.todaywas.ui.journal.create

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.journal.edit.HelpMeRefineUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState

@Immutable
data class AddJournalEntryUiState(
    val availableSlots: List<JournalDateSlotUiState>,
    val selectedSlot: JournalDateSlotUiState,
    val text: String,
    val isSaving: Boolean,
    val saveError: Boolean,
    val authState: AuthStateUi = AuthStateUi.Loading,
    val helpMeStart: HelpMeStartUiState = HelpMeStartUiState(),
    val helpMeRefine: HelpMeRefineUiState = HelpMeRefineUiState(),
    // One entry per JournalStarterPromptTone, each with a randomly picked variant — set once when
    // the ViewModel is created (see AddJournalEntryViewModel), not re-rolled while the screen is
    // open.
    val starterPrompts: List<JournalStarterPromptUiState> = emptyList(),
)
