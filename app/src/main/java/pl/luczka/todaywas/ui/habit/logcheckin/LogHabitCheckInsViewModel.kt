package pl.luczka.todaywas.ui.habit.logcheckin

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
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.domain.util.EditWindow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LogHabitCheckInsViewModel @Inject constructor(
    private val observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val logHabitCheckIns: LogHabitCheckInsUseCase,
) : ViewModel() {

    // Ascending (yesterday, then today) to match the existing chip display order; today stays the
    // default selection via .last().
    private val selectableDates = EditWindow.freshLoggableDates(Instant.now()).sorted()

    private val selectedDateFlow = MutableStateFlow(selectableDates.last())
    private val pendingValuesFlow = MutableStateFlow<Map<String, Int>>(emptyMap())

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
            is LogHabitCheckInsIntent.ValueChanged -> onValueChanged(intent.habitId, intent.value)
            LogHabitCheckInsIntent.SaveClicked -> onSaveClicked()
            LogHabitCheckInsIntent.CancelClicked -> onCancelClicked()
        }
    }

    private fun onDateSelected(date: LocalDate) {
        selectedDateFlow.value = date
        pendingValuesFlow.value = emptyMap()
    }

    private fun onValueChanged(
        habitId: String,
        value: Int?,
    ) {
        pendingValuesFlow.update { if (value == null) it - habitId else it + (habitId to value) }
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
}
