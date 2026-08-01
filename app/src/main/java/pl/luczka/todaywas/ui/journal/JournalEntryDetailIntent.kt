package pl.luczka.todaywas.ui.journal

sealed interface JournalEntryDetailIntent {

    data class Load(
        val id: Long,
    ) : JournalEntryDetailIntent

    data object EditClicked : JournalEntryDetailIntent

    data class TextChanged(
        val text: String,
    ) : JournalEntryDetailIntent

    data object SaveClicked : JournalEntryDetailIntent

    data object CancelEditClicked : JournalEntryDetailIntent

    data object BackClicked : JournalEntryDetailIntent
}
