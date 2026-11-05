package pl.luczka.todaywas.feature.journal.impl.detail

internal sealed interface JournalEntryDetailNavigation {

    data object EntryDeleted : JournalEntryDetailNavigation
}
