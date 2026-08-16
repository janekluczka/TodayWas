package pl.luczka.todaywas.ui.habit.create

sealed interface CreateHabitUiEvent {

    data object Saved : CreateHabitUiEvent

    data object Cancelled : CreateHabitUiEvent
}
