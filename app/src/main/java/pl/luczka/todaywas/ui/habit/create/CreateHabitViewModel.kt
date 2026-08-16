package pl.luczka.todaywas.ui.habit.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.usecase.CreateHabitUseCase
import javax.inject.Inject

@HiltViewModel
class CreateHabitViewModel @Inject constructor(
    private val createHabit: CreateHabitUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateHabitUiState(
            name = "",
            description = "",
            scaleSteps = HabitScaleStepsRange.first,
            isSaving = false,
            saveError = false,
        ),
    )
    val uiState: StateFlow<CreateHabitUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<CreateHabitUiEvent>(Channel.BUFFERED)
    val events: Flow<CreateHabitUiEvent> = eventChannel.receiveAsFlow()

    fun onIntent(intent: CreateHabitIntent) {
        when (intent) {
            is CreateHabitIntent.NameChanged -> onNameChanged(intent.name)
            is CreateHabitIntent.DescriptionChanged -> onDescriptionChanged(intent.description)
            is CreateHabitIntent.ScaleStepsChanged -> onScaleStepsChanged(intent.steps)
            CreateHabitIntent.SaveClicked -> onSaveClicked()
            CreateHabitIntent.CancelClicked -> onCancelClicked()
        }
    }

    private fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    private fun onDescriptionChanged(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    private fun onScaleStepsChanged(steps: Int) {
        _uiState.update { it.copy(scaleSteps = steps.coerceIn(HabitScaleStepsRange)) }
    }

    private fun onCancelClicked() {
        eventChannel.trySend(CreateHabitUiEvent.Cancelled)
    }

    private fun onSaveClicked() {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = createHabit(
                name = state.name,
                description = state.description.ifBlank { null },
                type = if (state.isBinary) HabitType.BINARY else HabitType.SCALE,
                scaleMin = if (state.isBinary) null else 1,
                scaleMax = if (state.isBinary) null else state.scaleSteps,
            )
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                eventChannel.trySend(CreateHabitUiEvent.Saved)
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
