package pl.luczka.todaywas.ui.journal.detail

sealed interface JournalEntryDetailIntent {

    data object ScreenEntered : JournalEntryDetailIntent

    data object EditClicked : JournalEntryDetailIntent

    data object BackClicked : JournalEntryDetailIntent

    data object DeleteClicked : JournalEntryDetailIntent

    data object DeleteConfirmed : JournalEntryDetailIntent

    data object DeleteDismissed : JournalEntryDetailIntent
}
