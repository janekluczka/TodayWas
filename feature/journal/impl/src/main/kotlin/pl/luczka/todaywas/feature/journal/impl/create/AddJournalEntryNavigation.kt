package pl.luczka.todaywas.feature.journal.impl.create

sealed interface AddJournalEntryNavigation {

    data object Saved : AddJournalEntryNavigation

    data object SignIn : AddJournalEntryNavigation
}
