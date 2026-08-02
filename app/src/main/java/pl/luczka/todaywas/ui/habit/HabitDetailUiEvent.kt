package pl.luczka.todaywas.ui.habit

sealed interface HabitDetailUiEvent {

    data object NavigatedBack : HabitDetailUiEvent
}
