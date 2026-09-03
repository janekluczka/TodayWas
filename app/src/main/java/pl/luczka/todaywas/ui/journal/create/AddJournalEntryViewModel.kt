package pl.luczka.todaywas.ui.journal.create

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
import pl.luczka.todaywas.domain.model.AiPromptResult
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
import kotlin.random.Random

@HiltViewModel
class AddJournalEntryViewModel @Inject constructor(
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val addJournalEntry: AddJournalEntryUseCase,
    private val requestJournalStarterPrompt: RequestJournalStarterPromptUseCase,
    random: Random,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddJournalEntryUiState(
            availableSlots = emptyList(),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "",
            isSaving = false,
            saveError = false,
            starterPrompts = JournalStarterPromptTone.entries.map { tone ->
                JournalStarterPromptUiState(
                    tone = tone,
                    variant = random.nextInt(STARTER_PROMPT_VARIANT_COUNT),
                )
            },
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
            AddJournalEntryIntent.SignInClicked -> onSignInClicked()
            is AddJournalEntryIntent.ToneSelected -> onToneSelected(intent.tone)
            is AddJournalEntryIntent.ThoughtsChanged -> onThoughtsChanged(intent.thoughts)
            AddJournalEntryIntent.GenerateClicked -> onGenerate()
            AddJournalEntryIntent.RegenerateClicked -> onGenerate()
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
        generateJob?.cancel()
        val step = if (_uiState.value.authState is AuthStateUi.SignedIn) {
            HelpMeStartStep.INPUT
        } else {
            HelpMeStartStep.SIGNED_OUT
        }
        _uiState.update {
            it.copy(
                helpMeStart = it.helpMeStart.resetForNewSession(isVisible = true, step = step),
            )
        }
    }

    private fun onHelpMeStartDismissed() {
        generateJob?.cancel()
        _uiState.update {
            it.copy(
                helpMeStart = it.helpMeStart.resetForNewSession(isVisible = false),
            )
        }
    }

    private fun onSignInClicked() {
        generateJob?.cancel()
        _uiState.update {
            it.copy(
                helpMeStart = it.helpMeStart.resetForNewSession(isVisible = false),
            )
        }
        eventChannel.trySend(AddJournalEntryUiEvent.NavigateToSignIn)
    }

    private fun onToneSelected(tone: JournalPromptToneUiState) {
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(selectedTone = tone)) }
    }

    private fun onThoughtsChanged(thoughts: String) {
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(thoughts = thoughts)) }
    }

    private fun onGenerate() {
        val helpMeStart = _uiState.value.helpMeStart
        val tone = helpMeStart.selectedTone ?: return
        if (helpMeStart.isGenerating) return
        // remainingToday is only known once a call has actually returned this session (see its
        // doc comment) -- null means "don't know yet", so the first call of a session is never
        // preemptively blocked here; the server is the real source of truth either way.
        if ((helpMeStart.remainingToday ?: 1) <= 0) return
        _uiState.update {
            it.copy(
                helpMeStart = it.helpMeStart.copy(isGenerating = true, error = null),
            )
        }
        generateJob = viewModelScope.launch {
            val result =
                requestJournalStarterPrompt(tone.toDomain(), helpMeStart.thoughts.ifBlank { null })
            _uiState.update { current ->
                current.copy(helpMeStart = current.helpMeStart.applyResult(result))
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

    // Preserves remainingToday — see its doc comment on HelpMeStartUiState — and only resets when
    // a new AddJournalEntryViewModel instance is created.
    private fun HelpMeStartUiState.resetForNewSession(
        isVisible: Boolean,
        step: HelpMeStartStep = HelpMeStartStep.INPUT,
    ): HelpMeStartUiState = copy(
        isVisible = isVisible,
        step = step,
        selectedTone = null,
        thoughts = "",
        generatedText = null,
        isGenerating = false,
        error = null,
    )

    private fun HelpMeStartUiState.applyResult(
        result: Result<AiPromptResult>,
    ): HelpMeStartUiState = result.fold(
        onSuccess = { prompt ->
            copy(
                step = HelpMeStartStep.PREVIEW,
                generatedText = prompt.text,
                isGenerating = false,
                error = null,
                remainingToday = prompt.remainingToday,
            )
        },
        onFailure = { throwable ->
            val error =
                (throwable as? AiAssistException)?.error?.toUiState()
                    ?: AiAssistErrorUiState.UNKNOWN
            copy(
                isGenerating = false,
                error = error,
                remainingToday = if (error == AiAssistErrorUiState.DAILY_LIMIT_REACHED) {
                    0
                } else {
                    remainingToday
                },
            )
        },
    )
}
