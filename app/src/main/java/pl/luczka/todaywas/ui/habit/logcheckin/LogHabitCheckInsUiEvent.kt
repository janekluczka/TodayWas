package pl.luczka.todaywas.ui.habit.logcheckin

sealed interface LogHabitCheckInsUiEvent {

    data object Saved : LogHabitCheckInsUiEvent

    data object Cancelled : LogHabitCheckInsUiEvent
}
