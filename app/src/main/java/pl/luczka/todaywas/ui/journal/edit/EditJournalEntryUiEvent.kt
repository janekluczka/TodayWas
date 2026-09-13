package pl.luczka.todaywas.ui.journal.edit

sealed interface EditJournalEntryUiEvent {

    data object Saved : EditJournalEntryUiEvent

    // Covers an explicit discard-confirm as well as an expired-mid-flow outcome (a save that lost
    // the 24h window, or a refine result that landed after it closed) -- in both cases there's
    // nothing to show, just pop back to the (now correctly read-only) Detail screen.
    data object Discarded : EditJournalEntryUiEvent

    data object NavigateToSignIn : EditJournalEntryUiEvent
}
