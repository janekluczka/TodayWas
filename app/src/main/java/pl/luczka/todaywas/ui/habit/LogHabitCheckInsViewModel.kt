package pl.luczka.todaywas.ui.habit

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
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitCheckInBoard
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LogHabitCheckInsViewModel @Inject constructor(
    private val observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val logHabitCheckIns: LogHabitCheckInsUseCase,
) : ViewModel() {

    private val selectableDates = run {
        val today = LocalDate.now()
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }

    private val selectedDateFlow = MutableStateFlow(selectableDates.last())
    private val pendingValuesFlow = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val _uiState = MutableStateFlow(
        LogHabitCheckInsUiState(
            selectableDates = selectableDates,
            selectedDate = selectableDates.last(),
            rows = emptyList(),
            isSaving = false,
            saveError = false,
        ),
    )
    val uiState: StateFlow<LogHabitCheckInsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<LogHabitCheckInsUiEvent>(Channel.BUFFERED)
    val events: Flow<LogHabitCheckInsUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                observeHabitCheckInBoard(),
                selectedDateFlow,
                pendingValuesFlow,
            ) { board, selectedDate, pendingValues ->
                selectedDate to board.toRows(selectedDate, pendingValues)
            }.collect { (selectedDate, rows) ->
                _uiState.update { it.copy(selectedDate = selectedDate, rows = rows) }
            }
        }
    }

    fun onIntent(intent: LogHabitCheckInsIntent) {
        when (intent) {
            is LogHabitCheckInsIntent.DateSelected -> onDateSelected(intent.date)
            is LogHabitCheckInsIntent.BinaryValueChanged -> onValueChanged(intent.habitId, if (intent.value) 1 else 0)
            is LogHabitCheckInsIntent.ScaleValueChanged -> onValueChanged(intent.habitId, intent.value)
            LogHabitCheckInsIntent.SaveClicked -> onSaveClicked()
            LogHabitCheckInsIntent.CancelClicked -> onCancelClicked()
        }
    }

    private fun onDateSelected(date: LocalDate) {
        selectedDateFlow.value = date
        pendingValuesFlow.value = emptyMap()
    }

    private fun onValueChanged(
        habitId: Long,
        value: Int,
    ) {
        pendingValuesFlow.update { it + (habitId to value) }
    }

    private fun onCancelClicked() {
        eventChannel.trySend(LogHabitCheckInsUiEvent.Cancelled)
    }

    private fun onSaveClicked() {
        if (_uiState.value.isSaving) return
        val values = pendingValuesFlow.value
        if (values.isEmpty()) return
        val date = selectedDateFlow.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = logHabitCheckIns(date, values)
            if (result.isSuccess) {
                pendingValuesFlow.value = emptyMap()
                _uiState.update { it.copy(isSaving = false) }
                eventChannel.trySend(LogHabitCheckInsUiEvent.Saved)
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = true,
                    )
                }
            }
        }
    }

    private fun HabitCheckInBoard.toRows(
        selectedDate: LocalDate,
        pendingValues: Map<Long, Int>,
    ): List<HabitCheckInRowUiState> = habits.map { habit ->
        val existing = checkIns.find { it.habitId == habit.id && it.date == selectedDate }
        if (existing != null) habit.toAlreadyLoggedRow(existing) else habit.toEditableRow(pendingValues[habit.id])
    }

    private fun Habit.toAlreadyLoggedRow(checkIn: HabitCheckIn): HabitCheckInRowUiState.AlreadyLogged =
        HabitCheckInRowUiState.AlreadyLogged(
            habitId = id,
            name = name,
            status = if (type == HabitType.BINARY) {
                HabitCheckInStatusUiState.LoggedBinary(done = checkIn.value == 1)
            } else {
                HabitCheckInStatusUiState.LoggedScale(value = checkIn.value)
            },
        )

    private fun Habit.toEditableRow(pendingValue: Int?): HabitCheckInRowUiState.Editable = when (type) {
        HabitType.BINARY ->
            HabitCheckInRowUiState.Editable.Binary(
                habitId = id,
                name = name,
                value = pendingValue?.let { it == 1 },
            )
        HabitType.SCALE ->
            HabitCheckInRowUiState.Editable.Scale(
                habitId = id,
                name = name,
                value = pendingValue,
                range = (scaleMin ?: 0)..(scaleMax ?: 0),
            )
    }
}
