package pl.luczka.todaywas.ui.journal

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState

@Immutable
data class AddJournalEntryUiState(
    val availableSlots: List<JournalDateSlotUiState>,
    val selectedSlot: JournalDateSlotUiState,
    val text: String,
    val isSaving: Boolean,
    val saveError: Boolean,
)
