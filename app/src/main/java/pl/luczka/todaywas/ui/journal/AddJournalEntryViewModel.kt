package pl.luczka.todaywas.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import pl.luczka.todaywas.domain.usecase.AddJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalStarterPromptUseCase
import pl.luczka.todaywas.ui.mapper.toDomain
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import javax.inject.Inject

@HiltViewModel
class AddJournalEntryViewModel @Inject constructor(
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val addJournalEntry: AddJournalEntryUseCase,
    private val requestJournalStarterPrompt: RequestJournalStarterPromptUseCase,
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

    // Tracks the in-flight generate/regenerate call so a stale response can never land after the
    // dialog session it belongs to has already been reset (dismissed, reopened, or accepted).
    private var generateJob: Job? = null

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
        viewModelScope.launch {
            observeAuthState().collect { authState ->
                _uiState.update { it.copy(authState = authState.toUiState()) }
            }
        }
    }

    fun onIntent(intent: AddJournalEntryIntent) {
        when (intent) {
            is AddJournalEntryIntent.SlotSelected -> onSlotSelected(intent.slot)
            is AddJournalEntryIntent.TextChanged -> onTextChanged(intent.text)
            AddJournalEntryIntent.SaveClicked -> onSaveClicked()
            AddJournalEntryIntent.CancelClicked -> onCancelClicked()
            AddJournalEntryIntent.HelpMeStartClicked -> onHelpMeStartClicked()
            AddJournalEntryIntent.HelpMeStartDismissed -> onHelpMeStartDismissed()
            is AddJournalEntryIntent.ToneSelected -> onToneSelected(intent.tone)
            is AddJournalEntryIntent.ThoughtsChanged -> onThoughtsChanged(intent.thoughts)
            AddJournalEntryIntent.GenerateClicked -> onGenerate(isRegenerate = false)
            AddJournalEntryIntent.RegenerateClicked -> onGenerate(isRegenerate = true)
            AddJournalEntryIntent.UseGeneratedTextClicked -> onUseGeneratedTextClicked()
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

    private fun onHelpMeStartClicked() {
        if (_uiState.value.authState !is AuthStateUi.SignedIn) return
        generateJob?.cancel()
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.resetForNewSession(isVisible = true)) }
    }

    private fun onHelpMeStartDismissed() {
        generateJob?.cancel()
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.resetForNewSession(isVisible = false)) }
    }

    private fun onToneSelected(tone: JournalPromptToneUiState) {
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(selectedTone = tone)) }
    }

    private fun onThoughtsChanged(thoughts: String) {
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(thoughts = thoughts)) }
    }

    private fun onGenerate(isRegenerate: Boolean) {
        val helpMeStart = _uiState.value.helpMeStart
        val tone = helpMeStart.selectedTone ?: return
        if (helpMeStart.isGenerating) return
        if (isRegenerate && helpMeStart.regenerationsUsed >= MAX_REGENERATIONS) return
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(isGenerating = true, error = null)) }
        generateJob = viewModelScope.launch {
            val result = requestJournalStarterPrompt(tone.toDomain(), helpMeStart.thoughts.ifBlank { null })
            _uiState.update { current ->
                current.copy(helpMeStart = current.helpMeStart.applyResult(result, isRegenerate))
            }
        }
    }

    private fun onUseGeneratedTextClicked() {
        val generatedText = _uiState.value.helpMeStart.generatedText ?: return
        generateJob?.cancel()
        _uiState.update {
            it.copy(
                text = generatedText,
                helpMeStart = it.helpMeStart.resetForNewSession(isVisible = false),
            )
        }
    }

    // Preserves regenerationsUsed — the cap persists across dialog close/reopen for this screen
    // visit and only resets when a new AddJournalEntryViewModel instance is created.
    private fun HelpMeStartUiState.resetForNewSession(isVisible: Boolean): HelpMeStartUiState = copy(
        isVisible = isVisible,
        step = HelpMeStartStep.INPUT,
        selectedTone = null,
        thoughts = "",
        generatedText = null,
        isGenerating = false,
        error = null,
    )

    private fun HelpMeStartUiState.applyResult(
        result: Result<String>,
        isRegenerate: Boolean,
    ): HelpMeStartUiState = result.fold(
        onSuccess = { text ->
            copy(
                step = HelpMeStartStep.PREVIEW,
                generatedText = text,
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
}
