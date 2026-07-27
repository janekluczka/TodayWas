package pl.luczka.todaywas.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.usecase.AddJournalEntryUseCase

@HiltViewModel(assistedFactory = AddJournalEntryViewModel.Factory::class)
class AddJournalEntryViewModel
    @AssistedInject
    constructor(
        @Assisted availableSlots: List<JournalDateSlot>,
        private val addJournalEntry: AddJournalEntryUseCase,
    ) : ViewModel() {

        @AssistedFactory
        interface Factory {
            fun create(availableSlots: List<JournalDateSlot>): AddJournalEntryViewModel
        }

        private val _uiState =
            MutableStateFlow(
                AddJournalEntryUiState(
                    availableSlots = availableSlots,
                    selectedSlot = availableSlots.first(),
                    text = "",
                    isSaving = false,
                    saveError = false,
                ),
            )
        val uiState: StateFlow<AddJournalEntryUiState> = _uiState.asStateFlow()

        private val eventChannel = Channel<AddJournalEntryUiEvent>(Channel.BUFFERED)
        val events: Flow<AddJournalEntryUiEvent> = eventChannel.receiveAsFlow()

        fun onIntent(intent: AddJournalEntryIntent) {
            when (intent) {
                is AddJournalEntryIntent.SlotSelected -> _uiState.update { it.copy(selectedSlot = intent.slot) }
                is AddJournalEntryIntent.TextChanged -> _uiState.update { it.copy(text = intent.text) }
                AddJournalEntryIntent.SaveClicked -> onSaveClicked()
                AddJournalEntryIntent.CancelClicked -> eventChannel.trySend(AddJournalEntryUiEvent.Cancelled)
            }
        }

        private fun onSaveClicked() {
            if (_uiState.value.isSaving) return
            val state = _uiState.value
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, saveError = false) }
                val result = addJournalEntry(state.selectedSlot, state.text)
                if (result.isSuccess) {
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.trySend(AddJournalEntryUiEvent.Saved)
                } else {
                    _uiState.update { it.copy(isSaving = false, saveError = true) }
                }
            }
        }
    }
