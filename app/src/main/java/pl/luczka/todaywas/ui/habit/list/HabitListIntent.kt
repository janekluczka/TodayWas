package pl.luczka.todaywas.ui.habit.list

import pl.luczka.todaywas.ui.model.HabitSortUiState
import pl.luczka.todaywas.ui.model.HabitUiState

sealed interface HabitListIntent {

    data class SortSelected(
        val sort: HabitSortUiState,
    ) : HabitListIntent

    data class HabitClicked(
        val habit: HabitUiState,
    ) : HabitListIntent
}
