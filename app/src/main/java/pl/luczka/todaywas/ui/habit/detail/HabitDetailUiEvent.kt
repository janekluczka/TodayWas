package pl.luczka.todaywas.ui.habit.detail

sealed interface HabitDetailUiEvent {

    data object NavigatedBack : HabitDetailUiEvent
}
