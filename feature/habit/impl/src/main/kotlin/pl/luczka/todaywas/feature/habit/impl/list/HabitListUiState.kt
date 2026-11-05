package pl.luczka.todaywas.feature.habit.impl.list

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.core.ui.model.HabitSortUiState
import pl.luczka.todaywas.core.ui.model.HabitUiState

@Immutable
data class HabitListUiState(
    val isLoading: Boolean = true,
    val habits: List<HabitUiState> = emptyList(),
    val selectedSort: HabitSortUiState = HabitSortUiState.RECENTLY_CHECKED_IN,
)
