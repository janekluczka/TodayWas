package pl.luczka.todaywas.ui.habit

sealed interface LogHabitCheckInsUiEvent {

    data object Saved : LogHabitCheckInsUiEvent

    data object Cancelled : LogHabitCheckInsUiEvent
}
