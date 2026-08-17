package pl.luczka.todaywas.ui.journal.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.AiAssistException
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.usecase.DeleteJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalRefinementPromptUseCase
import pl.luczka.todaywas.domain.usecase.UpdateJournalEntryUseCase
import pl.luczka.todaywas.domain.util.EditWindow
import pl.luczka.todaywas.ui.journal.create.MAX_REGENERATIONS
import pl.luczka.todaywas.ui.mapper.toDomain
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import java.time.Clock

@HiltViewModel(assistedFactory = JournalEntryDetailViewModel.Factory::class)
class JournalEntryDetailViewModel @AssistedInject constructor(
    @Assisted private val id: String,
    private val getJournalEntry: GetJournalEntryUseCase,
    private val updateJournalEntry: UpdateJournalEntryUseCase,
    private val deleteJournalEntry: DeleteJournalEntryUseCase,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val requestJournalRefinementPrompt: RequestJournalRefinementPromptUseCase,
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

    // Tracks the in-flight refine/regenerate call so a stale response can never land after the
    // dialog session it belongs to has already been reset (dismissed, reopened, or accepted).
    private var refineJob: Job? = null

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
        viewModelScope.launch {
            observeAuthState().collect { authState ->
                _uiState.update { it.copy(authState = authState.toUiState()) }
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
            JournalEntryDetailIntent.DeleteClicked -> onDeleteClicked()
            JournalEntryDetailIntent.DeleteConfirmed -> onDeleteConfirmed()
            JournalEntryDetailIntent.DeleteDismissed -> onDeleteDismissed()
            JournalEntryDetailIntent.HelpMeRefineClicked -> onHelpMeRefineClicked()
            JournalEntryDetailIntent.HelpMeRefineDismissed -> onHelpMeRefineDismissed()
            is JournalEntryDetailIntent.ToneSelected -> onToneSelected(intent.tone)
            JournalEntryDetailIntent.RefineClicked -> onRefine(isRegenerate = false)
            JournalEntryDetailIntent.RegenerateRefineClicked -> onRefine(isRegenerate = true)
            JournalEntryDetailIntent.UseRefinedTextClicked -> onUseRefinedTextClicked()
        }
    }

    private fun onEditClicked() {
        _uiState.update { it.copy(isEditing = true, editedText = it.entry?.text.orEmpty()) }
    }

    private fun onTextChanged(text: String) {
        _uiState.update { it.copy(editedText = text) }
    }

    private fun onCancelEditClicked() {
        refineJob?.cancel()
        _uiState.update {
            it.copy(
                isEditing = false,
                editedText = it.entry?.text.orEmpty(),
                helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = false),
            )
        }
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

    private fun onHelpMeRefineClicked() {
        val state = _uiState.value
        if (state.authState !is AuthStateUi.SignedIn) return
        if (!state.isEditing || !state.isEditable || state.editedText.isBlank()) return
        if (state.editedText.length > MAX_REFINE_TEXT_LENGTH) return
        refineJob?.cancel()
        _uiState.update { it.copy(helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = true)) }
    }

    private fun onHelpMeRefineDismissed() {
        refineJob?.cancel()
        _uiState.update { it.copy(helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = false)) }
    }

    private fun onToneSelected(tone: JournalPromptToneUiState) {
        _uiState.update { it.copy(helpMeRefine = it.helpMeRefine.copy(selectedTone = tone)) }
    }

    private fun onRefine(isRegenerate: Boolean) {
        val state = _uiState.value
        val helpMeRefine = state.helpMeRefine
        val tone = helpMeRefine.selectedTone ?: return
        val entry = state.entry ?: return
        if (helpMeRefine.isGenerating || !state.isEditable) return
        if (isRegenerate && helpMeRefine.regenerationsUsed >= MAX_REGENERATIONS) return
        _uiState.update { it.copy(helpMeRefine = it.helpMeRefine.copy(isGenerating = true, error = null)) }
        refineJob = viewModelScope.launch {
            val result = requestJournalRefinementPrompt(state.editedText, tone.toDomain())
            // The 24h window can close mid-request (the call takes 10-20s); a result that lands
            // after expiry must be discarded and treated exactly like a Save that lost the race.
            if (!EditWindow.isEditable(entry.createdAt, clock.instant())) {
                _uiState.update {
                    it.copy(
                        isEditing = false,
                        isEditable = false,
                        saveError = true,
                        helpMeRefine = HelpMeRefineUiState(),
                    )
                }
                return@launch
            }
            _uiState.update { current ->
                current.copy(helpMeRefine = current.helpMeRefine.applyResult(result, isRegenerate))
            }
        }
    }

    private fun onUseRefinedTextClicked() {
        val refinedText = _uiState.value.helpMeRefine.refinedText ?: return
        refineJob?.cancel()
        _uiState.update {
            it.copy(
                editedText = refinedText,
                helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = false),
            )
        }
    }

    // Preserves regenerationsUsed — the cap persists across dialog close/reopen for this screen
    // visit and only resets when a new JournalEntryDetailViewModel instance is created.
    private fun HelpMeRefineUiState.resetForNewSession(isVisible: Boolean): HelpMeRefineUiState = copy(
        isVisible = isVisible,
        step = HelpMeRefineStep.INPUT,
        selectedTone = null,
        refinedText = null,
        isGenerating = false,
        error = null,
    )

    private fun HelpMeRefineUiState.applyResult(
        result: Result<String>,
        isRegenerate: Boolean,
    ): HelpMeRefineUiState = result.fold(
        onSuccess = { text ->
            copy(
                step = HelpMeRefineStep.PREVIEW,
                refinedText = text,
                isGenerating = false,
                error = null,
                regenerationsUsed = if (isRegenerate) regenerationsUsed + 1 else regenerationsUsed,
            )
        },
        onFailure = { throwable ->
            val error = (throwable as? AiAssistException)?.error?.toUiState() ?: AiAssistErrorUiState.UNKNOWN
            copy(isGenerating = false, error = error)
        },
    )

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted id: String,
        ): JournalEntryDetailViewModel
    }
}
