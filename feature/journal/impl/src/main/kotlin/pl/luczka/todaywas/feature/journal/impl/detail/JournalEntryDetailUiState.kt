package pl.luczka.todaywas.feature.journal.impl.detail

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.core.ui.model.JournalEntryUiState

@Immutable
internal data class JournalEntryDetailUiState(
    val isLoading: Boolean,
    val entry: JournalEntryUiState?,
    val isEditable: Boolean,
    val isDeleteDialogVisible: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: Boolean = false,
    val pendingNavigation: JournalEntryDetailNavigation? = null,
)
