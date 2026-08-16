package pl.luczka.todaywas.ui.journal.create

import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

sealed interface AddJournalEntryIntent {

    data class SlotSelected(
        val slot: JournalDateSlotUiState,
    ) : AddJournalEntryIntent

    data class TextChanged(
        val text: String,
    ) : AddJournalEntryIntent

    data object SaveClicked : AddJournalEntryIntent

    data object CancelClicked : AddJournalEntryIntent

    data object HelpMeStartClicked : AddJournalEntryIntent

    data object HelpMeStartDismissed : AddJournalEntryIntent

    data class ToneSelected(
        val tone: JournalPromptToneUiState,
    ) : AddJournalEntryIntent

    data class ThoughtsChanged(
        val thoughts: String,
    ) : AddJournalEntryIntent

    data object GenerateClicked : AddJournalEntryIntent

    data object RegenerateClicked : AddJournalEntryIntent

    data object UseGeneratedTextClicked : AddJournalEntryIntent
}
