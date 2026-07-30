package pl.luczka.todaywas.ui.journal

import pl.luczka.todaywas.ui.model.JournalDateSlotUiState

sealed interface AddJournalEntryIntent {

    data class SlotSelected(
        val slot: JournalDateSlotUiState,
    ) : AddJournalEntryIntent

    data class TextChanged(
        val text: String,
    ) : AddJournalEntryIntent

    data object SaveClicked : AddJournalEntryIntent

    data object CancelClicked : AddJournalEntryIntent
}
