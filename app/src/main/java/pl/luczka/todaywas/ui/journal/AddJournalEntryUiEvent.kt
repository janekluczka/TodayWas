package pl.luczka.todaywas.ui.journal

sealed interface AddJournalEntryUiEvent {

    data object Saved : AddJournalEntryUiEvent

    data object Cancelled : AddJournalEntryUiEvent
}
