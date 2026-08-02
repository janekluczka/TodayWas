package pl.luczka.todaywas.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.usecase.UpdateHabitCheckInUseCase
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.model.toUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

private data class HabitDetailViewModelState(
    val isLoading: Boolean = true,
    val habitName: String = "",
    val type: HabitTypeUiState = HabitTypeUiState.BINARY,
    val range: IntRange = 0..0,
    val checkIns: List<HabitCheckIn> = emptyList(),
    val pendingValues: Map<LocalDate, Int> = emptyMap(),
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
) {
    fun toUiState(
        dates: List<LocalDate>,
        now: Instant,
    ): HabitDetailUiState = HabitDetailUiState(
        isLoading = isLoading,
        habitName = habitName,
        type = type,
        range = range,
        rows = checkIns.toHabitDetailRows(dates, pendingValues, now),
        isSaving = isSaving,
        saveError = saveError,
    )
}

@HiltViewModel(assistedFactory = HabitDetailViewModel.Factory::class)
class HabitDetailViewModel @AssistedInject constructor(
    @Assisted private val habitId: Long,
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val logHabitCheckIns: LogHabitCheckInsUseCase,
    private val updateHabitCheckIn: UpdateHabitCheckInUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val dates = run {
        val today = LocalDate.now()
        listOf(today.minusDays(1), today)
    }

    private val viewModelState = MutableStateFlow(HabitDetailViewModelState())

    val uiState: StateFlow<HabitDetailUiState> = viewModelState
        .map { it.toUiState(dates, clock.instant()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = viewModelState.value.toUiState(dates, clock.instant()),
        )

    private val eventChannel = Channel<HabitDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<HabitDetailUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeHabitCheckInBoard().collect { board ->
                val habit = board.habits.find { it.id == habitId } ?: return@collect
                val checkIns = board.checkIns.filter { it.habitId == habitId }
                viewModelState.update {
                    it.copy(
                        isLoading = false,
                        habitName = habit.name,
                        type = habit.type.toUiState(),
                        range = habit.detailRange(),
                        checkIns = checkIns,
                    )
                }
            }
        }
    }

    fun onIntent(intent: HabitDetailIntent) {
        when (intent) {
            is HabitDetailIntent.ValueChanged -> onValueChanged(intent.date, intent.value)
            HabitDetailIntent.SaveClicked -> onSaveClicked()
            HabitDetailIntent.BackClicked -> onBackClicked()
        }
    }

    private fun onValueChanged(
        date: LocalDate,
        value: Int?,
    ) {
        viewModelState.update { state ->
            state.copy(
                pendingValues = if (value == null) {
                    state.pendingValues - date
                } else {
                    state.pendingValues + (date to value)
                },
            )
        }
    }

    private fun onBackClicked() {
        eventChannel.trySend(HabitDetailUiEvent.NavigatedBack)
    }

    private fun onSaveClicked() {
        val state = viewModelState.value
        if (state.isSaving) return
        val pending = state.pendingValues
        if (pending.isEmpty()) return
        val checkInsByDate = state.checkIns.associateBy { it.date }
        viewModelScope.launch {
            viewModelState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val results = pending.map { (date, value) ->
                val existing = checkInsByDate[date]
                if (existing != null) {
                    updateHabitCheckIn(habitId, date, value, existing.createdAt)
                } else {
                    logHabitCheckIns(date, mapOf(habitId to value))
                }
            }
            if (results.all { it.isSuccess }) {
                viewModelState.update {
                    it.copy(
                        isSaving = false,
                        pendingValues = emptyMap(),
                    )
                }
            } else {
                viewModelState.update {
                    it.copy(
                        isSaving = false,
                        saveError = true,
                    )
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted habitId: Long,
        ): HabitDetailViewModel
    }
}
