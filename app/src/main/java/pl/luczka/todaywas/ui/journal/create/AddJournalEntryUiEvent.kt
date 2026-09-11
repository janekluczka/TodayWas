package pl.luczka.todaywas.ui.journal.create

sealed interface AddJournalEntryUiEvent {

    data object Saved : AddJournalEntryUiEvent

    data object Cancelled : AddJournalEntryUiEvent

    data object NavigateToSignIn : AddJournalEntryUiEvent
}
