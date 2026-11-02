package pl.luczka.todaywas.ui.habit.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.ui.mapper.toSortedHabitUiStates
import pl.luczka.todaywas.ui.model.HabitSortUiState
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HabitListViewModel @Inject constructor(
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val selectedSort = MutableStateFlow(HabitSortUiState.RECENTLY_CHECKED_IN)

    val uiState: StateFlow<HabitListUiState> =
        combine(observeHabitCheckInBoard(), selectedSort) { board, sort ->
            HabitListUiState(
                isLoading = false,
                habits = board.toSortedHabitUiStates(LocalDate.now(clock), sort),
                selectedSort = sort,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HabitListUiState(),
        )

    fun onIntent(intent: HabitListIntent) {
        when (intent) {
            is HabitListIntent.SortSelected -> selectedSort.update { intent.sort }
        }
    }
}
