package pl.luczka.todaywas.ui.habit.list

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.HabitSortUiState
import pl.luczka.todaywas.ui.model.HabitUiState

@Immutable
data class HabitListUiState(
    val isLoading: Boolean = true,
    val habits: List<HabitUiState> = emptyList(),
    val selectedSort: HabitSortUiState = HabitSortUiState.RECENTLY_CHECKED_IN,
)
