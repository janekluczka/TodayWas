package pl.luczka.todaywas.feature.habit.impl.list

import pl.luczka.todaywas.core.ui.model.HabitSortUiState

internal sealed interface HabitListIntent {

    data class SortSelected(
        val sort: HabitSortUiState,
    ) : HabitListIntent
}
