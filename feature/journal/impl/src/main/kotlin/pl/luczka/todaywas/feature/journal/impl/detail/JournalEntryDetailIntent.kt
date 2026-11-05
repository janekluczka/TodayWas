package pl.luczka.todaywas.feature.journal.impl.detail

sealed interface JournalEntryDetailIntent {

    data object ScreenEntered : JournalEntryDetailIntent

    data object NavigationHandled : JournalEntryDetailIntent

    data object DeleteClicked : JournalEntryDetailIntent

    data object DeleteConfirmed : JournalEntryDetailIntent

    data object DeleteDismissed : JournalEntryDetailIntent
}
