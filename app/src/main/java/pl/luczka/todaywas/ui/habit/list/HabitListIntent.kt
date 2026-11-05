package pl.luczka.todaywas.ui.habit.list

import pl.luczka.todaywas.core.ui.model.HabitSortUiState

sealed interface HabitListIntent {

    data class SortSelected(
        val sort: HabitSortUiState,
    ) : HabitListIntent
}
