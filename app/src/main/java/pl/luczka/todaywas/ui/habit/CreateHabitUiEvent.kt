package pl.luczka.todaywas.ui.habit

sealed interface CreateHabitUiEvent {

    data object Saved : CreateHabitUiEvent

    data object Cancelled : CreateHabitUiEvent
}
