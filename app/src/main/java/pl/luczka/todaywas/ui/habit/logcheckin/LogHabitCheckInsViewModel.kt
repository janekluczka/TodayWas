package pl.luczka.todaywas.ui.habit.logcheckin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.usecase.GetFreshLoggableDatesUseCase
import pl.luczka.todaywas.domain.usecase.LogHabitCheckInsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase

@HiltViewModel
class LogHabitCheckInsViewModel @Inject constructor(
    observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase,
    private val logHabitCheckIns: LogHabitCheckInsUseCase,
    getFreshLoggableDates: GetFreshLoggableDatesUseCase,
) : ViewModel() {

    private data class SaveState(
        val isSaving: Boolean = false,
        val saveError: Boolean = false,
        val pendingNavigation: LogHabitCheckInsNavigation? = null,
    )

    // Ascending (yesterday, then today) to match the existing chip display order; today stays the
    // default selection via .last().
    private val selectableDates = getFreshLoggableDates().sorted()

    private val selectedDateFlow = MutableStateFlow(selectableDates.last())
    private val pendingValuesFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val saveState = MutableStateFlow(SaveState())

    val uiState: StateFlow<LogHabitCheckInsUiState> =
        combine(
            observeHabitCheckInBoard(),
            selectedDateFlow,
            pendingValuesFlow,
            saveState,
        ) { board, selectedDate, pendingValues, save ->
            LogHabitCheckInsUiState(
                selectableDates = selectableDates,
                selectedDate = selectedDate,
                rows = board.toRows(selectedDate, pendingValues),
                isSaving = save.isSaving,
                saveError = save.saveError,
                pendingNavigation = save.pendingNavigation,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LogHabitCheckInsUiState(
                selectableDates = selectableDates,
                selectedDate = selectableDates.last(),
                rows = emptyList(),
                isSaving = false,
                saveError = false,
            ),
        )

    fun onIntent(intent: LogHabitCheckInsIntent) {
        when (intent) {
            is LogHabitCheckInsIntent.DateSelected -> onDateSelected(intent.date)
            is LogHabitCheckInsIntent.ValueChanged -> onValueChanged(intent.habitId, intent.value)
            LogHabitCheckInsIntent.SaveClicked -> onSaveClicked()
            LogHabitCheckInsIntent.NavigationHandled ->
                saveState.update { it.copy(pendingNavigation = null) }
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

    private fun onSaveClicked() {
        if (saveState.value.isSaving) return
        val values = pendingValuesFlow.value
        if (values.isEmpty()) return
        val date = selectedDateFlow.value
        viewModelScope.launch {
            saveState.update { it.copy(isSaving = true, saveError = false) }
            val result = logHabitCheckIns(date, values)
            if (result.isSuccess) {
                pendingValuesFlow.value = emptyMap()
                saveState.update {
                    it.copy(isSaving = false, pendingNavigation = LogHabitCheckInsNavigation.Saved)
                }
            } else {
                saveState.update { it.copy(isSaving = false, saveError = true) }
            }
        }
    }
}
