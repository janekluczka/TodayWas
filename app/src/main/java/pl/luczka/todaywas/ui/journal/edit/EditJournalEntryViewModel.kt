package pl.luczka.todaywas.ui.journal.edit

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
import pl.luczka.todaywas.domain.model.AiPromptResult
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.IsEditableUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalRefinementPromptUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalStarterPromptUseCase
import pl.luczka.todaywas.domain.usecase.UpdateJournalEntryUseCase
import pl.luczka.todaywas.ui.journal.create.HelpMeStartStep
import pl.luczka.todaywas.ui.journal.create.HelpMeStartUiState
import pl.luczka.todaywas.ui.journal.create.JournalStarterPromptTone
import pl.luczka.todaywas.ui.journal.create.JournalStarterPromptUiState
import pl.luczka.todaywas.ui.journal.create.STARTER_PROMPT_VARIANT_COUNT
import pl.luczka.todaywas.ui.mapper.toDomain
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import kotlin.random.Random

@HiltViewModel(assistedFactory = EditJournalEntryViewModel.Factory::class)
class EditJournalEntryViewModel @AssistedInject constructor(
    @Assisted private val id: String,
    private val getJournalEntry: GetJournalEntryUseCase,
    private val updateJournalEntry: UpdateJournalEntryUseCase,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val requestJournalStarterPrompt: RequestJournalStarterPromptUseCase,
    private val requestJournalRefinementPrompt: RequestJournalRefinementPromptUseCase,
    private val isEditable: IsEditableUseCase,
    random: Random,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditJournalEntryUiState(
            isLoading = true,
            entry = null,
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
    val uiState: StateFlow<EditJournalEntryUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditJournalEntryUiEvent>(Channel.BUFFERED)
    val events: Flow<EditJournalEntryUiEvent> = eventChannel.receiveAsFlow()

    // Tracks the in-flight generate/regenerate call so a stale response can never land after the
    // sheet session it belongs to has already been reset (dismissed, reopened, or accepted).
    private var generateJob: Job? = null

    // Same purpose as generateJob, for the independent refine sheet.
    private var refineJob: Job? = null

    init {
        viewModelScope.launch {
            val entry = getJournalEntry(id) ?: return@launch
            _uiState.update {
                it.copy(
                    isLoading = false,
                    entry = entry.toUiState(),
                    text = entry.text,
                )
            }
        }
        viewModelScope.launch {
            observeAuthState().collect { authState ->
                _uiState.update { it.copy(authState = authState.toUiState()) }
            }
        }
    }

    fun onIntent(intent: EditJournalEntryIntent) {
        when (intent) {
            is EditJournalEntryIntent.TextChanged -> onTextChanged(intent.text)
            EditJournalEntryIntent.SaveClicked -> onSaveClicked()
            EditJournalEntryIntent.BackClicked -> onBackClicked()
            EditJournalEntryIntent.DiscardConfirmed -> onDiscardConfirmed()
            EditJournalEntryIntent.DiscardDismissed -> onDiscardDismissed()
            EditJournalEntryIntent.SignInClicked -> onSignInClicked()
            EditJournalEntryIntent.HelpMeStartClicked -> onHelpMeStartClicked()
            EditJournalEntryIntent.HelpMeStartDismissed -> onHelpMeStartDismissed()
            is EditJournalEntryIntent.HelpMeStartToneSelected -> onHelpMeStartToneSelected(
                intent.tone,
            )
            is EditJournalEntryIntent.HelpMeStartThoughtsChanged -> onHelpMeStartThoughtsChanged(
                intent.thoughts,
            )
            EditJournalEntryIntent.GenerateClicked -> onGenerate()
            EditJournalEntryIntent.RegenerateClicked -> onGenerate()
            EditJournalEntryIntent.UseGeneratedTextClicked -> onUseGeneratedTextClicked()
            EditJournalEntryIntent.HelpMeRefineClicked -> onHelpMeRefineClicked()
            EditJournalEntryIntent.HelpMeRefineDismissed -> onHelpMeRefineDismissed()
            is EditJournalEntryIntent.HelpMeRefineToneSelected -> onHelpMeRefineToneSelected(
                intent.tone,
            )
            is EditJournalEntryIntent.HelpMeRefineThoughtsChanged -> onHelpMeRefineThoughtsChanged(
                intent.thoughts,
            )
            EditJournalEntryIntent.RefineClicked -> onRefine()
            EditJournalEntryIntent.RegenerateRefineClicked -> onRefine()
            EditJournalEntryIntent.UseRefinedTextClicked -> onUseRefinedTextClicked()
        }
    }

    private fun onTextChanged(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    private fun onSaveClicked() {
        val state = _uiState.value
        if (state.isSaving) return
        val entry = state.entry ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = false) }
            val result = updateJournalEntry(entry.id, state.text, entry.createdAt)
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                eventChannel.trySend(EditJournalEntryUiEvent.Saved)
            } else if (result.exceptionOrNull() is EditWindowExpiredException) {
                _uiState.update { it.copy(isSaving = false) }
                eventChannel.trySend(EditJournalEntryUiEvent.Discarded)
            } else {
                _uiState.update { it.copy(isSaving = false, saveError = true) }
            }
        }
    }

    private fun onBackClicked() {
        val state = _uiState.value
        if (state.text == state.entry?.text.orEmpty()) {
            eventChannel.trySend(EditJournalEntryUiEvent.Discarded)
        } else {
            _uiState.update { it.copy(isDiscardConfirmVisible = true) }
        }
    }

    private fun onDiscardConfirmed() {
        eventChannel.trySend(EditJournalEntryUiEvent.Discarded)
    }

    private fun onDiscardDismissed() {
        _uiState.update { it.copy(isDiscardConfirmVisible = false) }
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
        eventChannel.trySend(EditJournalEntryUiEvent.NavigateToSignIn)
    }

    private fun onHelpMeStartToneSelected(tone: JournalPromptToneUiState) {
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(selectedTone = tone)) }
    }

    private fun onHelpMeStartThoughtsChanged(thoughts: String) {
        _uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(thoughts = thoughts)) }
    }

    private fun onGenerate() {
        val state = _uiState.value
        val helpMeStart = state.helpMeStart
        val tone = helpMeStart.selectedTone ?: return
        val entry = state.entry ?: return
        if (helpMeStart.isGenerating) return
        if ((helpMeStart.remainingToday ?: 1) <= 0) return
        _uiState.update {
            it.copy(
                helpMeStart = it.helpMeStart.copy(isGenerating = true, error = null),
            )
        }
        generateJob = viewModelScope.launch {
            val result =
                requestJournalStarterPrompt(tone.toDomain(), helpMeStart.thoughts.ifBlank { null })
            // Mirrors onRefine's expired-mid-flight handling: the 24h window can close during the
            // 10-20s call, and this screen has no non-editing fallback to show instead.
            if (!isEditable(entry.createdAt)) {
                _uiState.update { it.copy(helpMeStart = HelpMeStartUiState()) }
                eventChannel.trySend(EditJournalEntryUiEvent.Discarded)
                return@launch
            }
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

    private fun onHelpMeRefineClicked() {
        val state = _uiState.value
        if (state.authState !is AuthStateUi.SignedIn) return
        if (state.text.isBlank() || state.text.length > MAX_REFINE_TEXT_LENGTH) return
        refineJob?.cancel()
        _uiState.update {
            it.copy(
                helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = true),
            )
        }
    }

    private fun onHelpMeRefineDismissed() {
        refineJob?.cancel()
        _uiState.update {
            it.copy(
                helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = false),
            )
        }
    }

    private fun onHelpMeRefineToneSelected(tone: JournalPromptToneUiState) {
        _uiState.update { it.copy(helpMeRefine = it.helpMeRefine.copy(selectedTone = tone)) }
    }

    private fun onHelpMeRefineThoughtsChanged(thoughts: String) {
        _uiState.update { it.copy(helpMeRefine = it.helpMeRefine.copy(thoughts = thoughts)) }
    }

    private fun onRefine() {
        val state = _uiState.value
        val helpMeRefine = state.helpMeRefine
        val tone = helpMeRefine.selectedTone ?: return
        val entry = state.entry ?: return
        if (helpMeRefine.isGenerating) return
        if ((helpMeRefine.remainingToday ?: 1) <= 0) return
        _uiState.update {
            it.copy(
                helpMeRefine = it.helpMeRefine.copy(isGenerating = true, error = null),
            )
        }
        refineJob = viewModelScope.launch {
            val result = requestJournalRefinementPrompt(
                state.text,
                tone.toDomain(),
                helpMeRefine.thoughts.ifBlank { null },
            )
            // The 24h window can close mid-request (the call takes 10-20s). Unlike the old
            // Detail-hosted edit sheet, this screen has no non-editing display mode to fall back
            // into, so a result landing after expiry is discarded the same way an explicit
            // discard is: pop back to Detail, which re-fetches and shows the (untouched) entry
            // read-only.
            if (!isEditable(entry.createdAt)) {
                _uiState.update { it.copy(helpMeRefine = HelpMeRefineUiState()) }
                eventChannel.trySend(EditJournalEntryUiEvent.Discarded)
                return@launch
            }
            _uiState.update { current ->
                current.copy(helpMeRefine = current.helpMeRefine.applyResult(result))
            }
        }
    }

    private fun onUseRefinedTextClicked() {
        val refinedText = _uiState.value.helpMeRefine.refinedText ?: return
        refineJob?.cancel()
        _uiState.update {
            it.copy(
                text = refinedText,
                helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = false),
            )
        }
    }

    // Preserves remainingToday — see its doc comment on HelpMeStartUiState — and only resets when
    // a new EditJournalEntryViewModel instance is created.
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

    // Preserves remainingToday, mirroring HelpMeStartUiState's resetForNewSession.
    private fun HelpMeRefineUiState.resetForNewSession(
        isVisible: Boolean,
    ): HelpMeRefineUiState = copy(
        isVisible = isVisible,
        step = HelpMeRefineStep.INPUT,
        selectedTone = null,
        thoughts = "",
        refinedText = null,
        isGenerating = false,
        error = null,
    )

    private fun HelpMeRefineUiState.applyResult(
        result: Result<AiPromptResult>,
    ): HelpMeRefineUiState = result.fold(
        onSuccess = { prompt ->
            copy(
                step = HelpMeRefineStep.PREVIEW,
                refinedText = prompt.text,
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

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted id: String,
        ): EditJournalEntryViewModel
    }
}
