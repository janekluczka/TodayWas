package pl.luczka.todaywas.ui.journal

import pl.luczka.todaywas.domain.model.JournalDateSlot

sealed interface AddJournalEntryIntent {

    data class SlotSelected(
        val slot: JournalDateSlot,
    ) : AddJournalEntryIntent

    data class TextChanged(
        val text: String,
    ) : AddJournalEntryIntent

    data object SaveClicked : AddJournalEntryIntent

    data object CancelClicked : AddJournalEntryIntent
}
