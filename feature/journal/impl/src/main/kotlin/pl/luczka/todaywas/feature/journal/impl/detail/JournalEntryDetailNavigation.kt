package pl.luczka.todaywas.feature.journal.impl.detail

sealed interface JournalEntryDetailNavigation {

    data object EntryDeleted : JournalEntryDetailNavigation
}
