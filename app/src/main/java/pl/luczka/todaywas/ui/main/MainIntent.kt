package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.domain.model.JournalEntry

sealed interface MainIntent {

    data object AddEntryClicked : MainIntent

    data class JournalEntryClicked(
        val entry: JournalEntry,
    ) : MainIntent
}
