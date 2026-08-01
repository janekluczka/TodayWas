package pl.luczka.todaywas.ui.journal

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
import pl.luczka.todaywas.domain.usecase.AddJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.toDomain
import pl.luczka.todaywas.ui.model.toUiState
import javax.inject.Inject

@HiltViewModel
class AddJournalEntryViewModel @Inject constructor(
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    private val addJournalEntry: AddJournalEntryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddJournalEntryUiState(
            availableSlots = emptyList(),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "",
            isSaving = false,
            saveError = false,
        ),
    )
    val uiState: StateFlow<AddJournalEntryUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<AddJournalEntryUiEvent>(Channel.BUFFERED)
    val events: Flow<AddJournalEntryUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeAddableJournalDateSlots().collect { slots ->
                val availableSlots = slots.map { it.toUiState() }
                _uiState.update { current ->
                    val selectedSlot = if (current.selectedSlot in availableSlots) {
                        current.selectedSlot
                    } else {
                        availableSlots.firstOrNull() ?: current.selectedSlot
                    }
                    current.copy(
                        availableSlots = availableSlots,
                        selectedSlot = selectedSlot,
                    )
                }
            }
        }
    }

    fun onIntent(intent: AddJournalEntryIntent) {
        when (intent) {
            is AddJournalEntryIntent.SlotSelected -> onSlotSelected(intent.slot)
            is AddJournalEntryIntent.TextChanged -> onTextChanged(intent.text)
            AddJournalEntryIntent.SaveClicked -> onSaveClicked()
            AddJournalEntryIntent.CancelClicked -> onCancelClicked()
        }
    }

    private fun onSlotSelected(slot: JournalDateSlotUiState) {
        _uiState.update { it.copy(selectedSlot = slot) }
    }

    private fun onTextChanged(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    private fun onCancelClicked() {
        eventChannel.trySend(AddJournalEntryUiEvent.Cancelled)
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
            val result = addJournalEntry(state.selectedSlot.toDomain(), state.text)
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                eventChannel.trySend(AddJournalEntryUiEvent.Saved)
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
