package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.ui.model.JournalEntryUiState

sealed interface MainUiEvent {

    data object NavigateToAddEntry : MainUiEvent

    data class NavigateToJournalDetail(
        val entry: JournalEntryUiState,
    ) : MainUiEvent

    data object NavigateToCreateHabit : MainUiEvent

    data object NavigateToLogHabitCheckIns : MainUiEvent

    data class NavigateToHabitDetail(
        val habitId: String,
    ) : MainUiEvent

    data object NavigateToAccount : MainUiEvent
}
