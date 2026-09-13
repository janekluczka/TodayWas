package pl.luczka.todaywas.ui.journal.detail

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
import pl.luczka.todaywas.domain.usecase.DeleteJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.IsEditableUseCase
import pl.luczka.todaywas.ui.mapper.toUiState

@HiltViewModel(assistedFactory = JournalEntryDetailViewModel.Factory::class)
class JournalEntryDetailViewModel @AssistedInject constructor(
    @Assisted private val id: String,
    private val getJournalEntry: GetJournalEntryUseCase,
    private val deleteJournalEntry: DeleteJournalEntryUseCase,
    private val isEditable: IsEditableUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JournalEntryDetailUiState(
            isLoading = true,
            entry = null,
            isEditable = false,
        ),
    )
    val uiState: StateFlow<JournalEntryDetailUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<JournalEntryDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<JournalEntryDetailUiEvent> = eventChannel.receiveAsFlow()

    fun onIntent(intent: JournalEntryDetailIntent) {
        when (intent) {
            JournalEntryDetailIntent.ScreenEntered -> onScreenEntered()
            JournalEntryDetailIntent.EditClicked -> onEditClicked()
            JournalEntryDetailIntent.BackClicked -> onBackClicked()
            JournalEntryDetailIntent.DeleteClicked -> onDeleteClicked()
            JournalEntryDetailIntent.DeleteConfirmed -> onDeleteConfirmed()
            JournalEntryDetailIntent.DeleteDismissed -> onDeleteDismissed()
        }
    }

    // Runs every time this screen (re)enters composition -- including the very first load and
    // every return from Edit -- since JournalEntryDetailViewModel is retained across that nav
    // round-trip (rememberViewModelStoreNavEntryDecorator) and would otherwise keep showing
    // whatever entry/isEditable snapshot it loaded once, stale after a save.
    private fun onScreenEntered() {
        viewModelScope.launch {
            val entry = getJournalEntry(id) ?: return@launch
            _uiState.update {
                it.copy(
                    isLoading = false,
                    entry = entry.toUiState(),
                    isEditable = isEditable(entry.createdAt),
                )
            }
        }
    }

    private fun onEditClicked() {
        eventChannel.trySend(JournalEntryDetailUiEvent.NavigateToEdit)
    }

    private fun onBackClicked() {
        eventChannel.trySend(JournalEntryDetailUiEvent.NavigatedBack)
    }

    private fun onDeleteClicked() {
        _uiState.update { it.copy(isDeleteDialogVisible = true) }
    }

    private fun onDeleteDismissed() {
        _uiState.update { it.copy(isDeleteDialogVisible = false) }
    }

    private fun onDeleteConfirmed() {
        val state = _uiState.value
        if (state.isDeleting) return
        val entry = state.entry ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, deleteError = false) }
            val result = deleteJournalEntry(entry.id)
            if (result.isSuccess) {
                eventChannel.trySend(JournalEntryDetailUiEvent.NavigatedBack)
            } else {
                _uiState.update {
                    it.copy(isDeleting = false, deleteError = true, isDeleteDialogVisible = false)
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted id: String,
        ): JournalEntryDetailViewModel
    }
}
