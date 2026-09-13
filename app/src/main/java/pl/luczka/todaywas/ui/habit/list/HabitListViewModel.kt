package pl.luczka.todaywas.ui.habit.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _uiState = MutableStateFlow(HabitListUiState())
    val uiState: StateFlow<HabitListUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<HabitListUiEvent>(Channel.BUFFERED)
    val events: Flow<HabitListUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(observeHabitCheckInBoard(), selectedSort) { board, sort ->
                board.toSortedHabitUiStates(LocalDate.now(clock), clock.instant(), sort) to sort
            }.collect { (sorted, sort) ->
                _uiState.update {
                    it.copy(isLoading = false, habits = sorted, selectedSort = sort)
                }
            }
        }
    }

    fun onIntent(intent: HabitListIntent) {
        when (intent) {
            is HabitListIntent.SortSelected -> selectedSort.update { intent.sort }
            is HabitListIntent.HabitClicked -> eventChannel.trySend(
                HabitListUiEvent.NavigateToDetail(intent.habit.id),
            )
        }
    }
}
