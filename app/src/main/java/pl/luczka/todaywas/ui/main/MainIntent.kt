package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState

sealed interface MainIntent {

    data class FabActionClicked(
        val action: FabActionUiState,
    ) : MainIntent

    data object FabToggled : MainIntent

    data class JournalEntryClicked(
        val entry: JournalEntryUiState,
    ) : MainIntent

    data class HabitClicked(
        val habit: HabitUiState,
    ) : MainIntent
}
