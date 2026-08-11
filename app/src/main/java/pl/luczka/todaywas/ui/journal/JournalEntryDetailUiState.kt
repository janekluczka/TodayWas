package pl.luczka.todaywas.ui.journal

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Immutable
data class JournalEntryDetailUiState(
    val isLoading: Boolean,
    val entry: JournalEntryUiState?,
    val editedText: String,
    val isEditable: Boolean,
    val isEditing: Boolean,
    val isSaving: Boolean,
    val saveError: Boolean,
    val authState: AuthStateUi = AuthStateUi.Loading,
    val helpMeRefine: HelpMeRefineUiState = HelpMeRefineUiState(),
)
