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
import pl.luczka.todaywas.domain.model.EditWindow
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.UpdateJournalEntryUseCase
import pl.luczka.todaywas.ui.model.toUiState
import java.time.Clock

@HiltViewModel(assistedFactory = JournalEntryDetailViewModel.Factory::class)
class JournalEntryDetailViewModel @AssistedInject constructor(
    @Assisted private val id: String,
    private val getJournalEntry: GetJournalEntryUseCase,
    private val updateJournalEntry: UpdateJournalEntryUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JournalEntryDetailUiState(
            isLoading = true,
            entry = null,
            editedText = "",
            isEditable = false,
            isEditing = false,
            isSaving = false,
            saveError = false,
        ),
    )
    val uiState: StateFlow<JournalEntryDetailUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<JournalEntryDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<JournalEntryDetailUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val entry = getJournalEntry(id) ?: return@launch
            _uiState.update {
                it.copy(
                    isLoading = false,
                    entry = entry.toUiState(),
                    editedText = entry.text,
                    isEditable = EditWindow.isEditable(entry.createdAt, clock.instant()),
                )
            }
        }
    }

    fun onIntent(intent: JournalEntryDetailIntent) {
        when (intent) {
            JournalEntryDetailIntent.EditClicked -> onEditClicked()
            is JournalEntryDetailIntent.TextChanged -> onTextChanged(intent.text)
            JournalEntryDetailIntent.SaveClicked -> onSaveClicked()
            JournalEntryDetailIntent.CancelEditClicked -> onCancelEditClicked()
            JournalEntryDetailIntent.BackClicked -> onBackClicked()
        }
    }

    private fun onEditClicked() {
        _uiState.update { it.copy(isEditing = true, editedText = it.entry?.text.orEmpty()) }
    }

    private fun onTextChanged(text: String) {
        _uiState.update { it.copy(editedText = text) }
    }

    private fun onCancelEditClicked() {
        _uiState.update { it.copy(isEditing = false, editedText = it.entry?.text.orEmpty()) }
    }

    private fun onSaveClicked() {
        val state = _uiState.value
        if (state.isSaving) return
        val entry = state.entry ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = false) }
            val result = updateJournalEntry(entry.id, state.editedText, entry.createdAt)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isEditing = false,
                        entry = it.entry?.copy(text = state.editedText),
                    )
                }
            } else {
                val expired = result.exceptionOrNull() is EditWindowExpiredException
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = true,
                        isEditing = if (expired) false else it.isEditing,
                        isEditable = if (expired) false else it.isEditable,
                    )
                }
            }
        }
    }

    private fun onBackClicked() {
        eventChannel.trySend(JournalEntryDetailUiEvent.NavigatedBack)
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted id: String,
        ): JournalEntryDetailViewModel
    }
}
