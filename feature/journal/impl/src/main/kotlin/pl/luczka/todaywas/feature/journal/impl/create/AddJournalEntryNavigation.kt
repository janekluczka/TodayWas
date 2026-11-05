package pl.luczka.todaywas.feature.journal.impl.create

internal sealed interface AddJournalEntryNavigation {

    data object Saved : AddJournalEntryNavigation

    data object SignIn : AddJournalEntryNavigation
}
