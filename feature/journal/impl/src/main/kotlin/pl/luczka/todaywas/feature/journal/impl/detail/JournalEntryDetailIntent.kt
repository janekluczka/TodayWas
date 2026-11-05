package pl.luczka.todaywas.feature.journal.impl.detail

internal sealed interface JournalEntryDetailIntent {

    data object NavigationHandled : JournalEntryDetailIntent

    data object DeleteClicked : JournalEntryDetailIntent

    data object DeleteConfirmed : JournalEntryDetailIntent

    data object DeleteDismissed : JournalEntryDetailIntent
}
