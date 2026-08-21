package pl.luczka.todaywas.ui.habit.list

sealed interface HabitListUiEvent {

    data class NavigateToDetail(
        val habitId: String,
    ) : HabitListUiEvent
}
