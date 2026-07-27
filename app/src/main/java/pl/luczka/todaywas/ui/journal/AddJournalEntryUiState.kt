package pl.luczka.todaywas.ui.journal

import pl.luczka.todaywas.domain.model.JournalDateSlot

data class AddJournalEntryUiState(
    val availableSlots: List<JournalDateSlot>,
    val selectedSlot: JournalDateSlot,
    val text: String,
    val isSaving: Boolean,
    val saveError: Boolean,
)
