package pl.luczka.todaywas.ui.journal.detail

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Immutable
data class JournalEntryDetailUiState(
    val isLoading: Boolean,
    val entry: JournalEntryUiState?,
    val isEditable: Boolean,
    val isDeleteDialogVisible: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: Boolean = false,
)
