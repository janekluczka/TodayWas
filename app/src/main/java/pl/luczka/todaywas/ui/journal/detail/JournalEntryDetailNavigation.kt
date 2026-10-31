package pl.luczka.todaywas.ui.journal.detail

sealed interface JournalEntryDetailNavigation {

    data object EntryDeleted : JournalEntryDetailNavigation
}
