package pl.luczka.todaywas.ui.journal.edit

import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

sealed interface EditJournalEntryIntent {

    data class TextChanged(
        val text: String,
    ) : EditJournalEntryIntent

    data object SaveClicked : EditJournalEntryIntent

    data object BackClicked : EditJournalEntryIntent

    data object DiscardConfirmed : EditJournalEntryIntent

    data object DiscardDismissed : EditJournalEntryIntent

    data object SignInClicked : EditJournalEntryIntent

    data object HelpMeStartClicked : EditJournalEntryIntent

    data object HelpMeStartDismissed : EditJournalEntryIntent

    data class HelpMeStartToneSelected(
        val tone: JournalPromptToneUiState,
    ) : EditJournalEntryIntent

    data class HelpMeStartThoughtsChanged(
        val thoughts: String,
    ) : EditJournalEntryIntent

    data object GenerateClicked : EditJournalEntryIntent

    data object RegenerateClicked : EditJournalEntryIntent

    data object UseGeneratedTextClicked : EditJournalEntryIntent

    data object HelpMeRefineClicked : EditJournalEntryIntent

    data object HelpMeRefineDismissed : EditJournalEntryIntent

    data class HelpMeRefineToneSelected(
        val tone: JournalPromptToneUiState,
    ) : EditJournalEntryIntent

    data class HelpMeRefineThoughtsChanged(
        val thoughts: String,
    ) : EditJournalEntryIntent

    data object RefineClicked : EditJournalEntryIntent

    data object RegenerateRefineClicked : EditJournalEntryIntent

    data object UseRefinedTextClicked : EditJournalEntryIntent
}
