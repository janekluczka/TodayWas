package pl.luczka.todaywas.ui.journal.detail

import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

sealed interface JournalEntryDetailIntent {

    data object EditClicked : JournalEntryDetailIntent

    data class TextChanged(
        val text: String,
    ) : JournalEntryDetailIntent

    data object SaveClicked : JournalEntryDetailIntent

    data object CancelEditClicked : JournalEntryDetailIntent

    data object BackClicked : JournalEntryDetailIntent

    data object HelpMeRefineClicked : JournalEntryDetailIntent

    data object HelpMeRefineDismissed : JournalEntryDetailIntent

    data class ToneSelected(
        val tone: JournalPromptToneUiState,
    ) : JournalEntryDetailIntent

    data object RefineClicked : JournalEntryDetailIntent

    data object RegenerateRefineClicked : JournalEntryDetailIntent

    data object UseRefinedTextClicked : JournalEntryDetailIntent
}
