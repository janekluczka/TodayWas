package pl.luczka.todaywas.ui.journal.create

sealed interface AddJournalEntryNavigation {

    data object Saved : AddJournalEntryNavigation

    data object SignIn : AddJournalEntryNavigation
}
