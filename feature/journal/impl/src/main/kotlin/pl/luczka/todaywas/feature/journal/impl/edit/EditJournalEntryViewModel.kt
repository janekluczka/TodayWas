package pl.luczka.todaywas.feature.journal.impl.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.core.domain.model.AiAssistException
import pl.luczka.todaywas.core.domain.model.AiPromptResult
import pl.luczka.todaywas.core.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.core.domain.usecase.IsEditableUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveJournalEntryUseCase
import pl.luczka.todaywas.core.domain.usecase.RequestJournalRefinementPromptUseCase
import pl.luczka.todaywas.core.domain.usecase.RequestJournalStarterPromptUseCase
import pl.luczka.todaywas.core.domain.usecase.UpdateJournalEntryUseCase
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.feature.journal.impl.create.HelpMeStartStep
import pl.luczka.todaywas.feature.journal.impl.create.HelpMeStartUiState
import pl.luczka.todaywas.feature.journal.impl.create.JournalStarterPromptTone
import pl.luczka.todaywas.feature.journal.impl.create.JournalStarterPromptUiState
import pl.luczka.todaywas.feature.journal.impl.create.STARTER_PROMPT_VARIANT_COUNT
import pl.luczka.todaywas.feature.journal.impl.mapper.toDomain
import pl.luczka.todaywas.feature.journal.impl.mapper.toUiState
import pl.luczka.todaywas.feature.journal.impl.model.AiAssistErrorUiState
import pl.luczka.todaywas.feature.journal.impl.model.JournalPromptToneUiState

@HiltViewModel(assistedFactory = EditJournalEntryViewModel.Factory::class)
internal class EditJournalEntryViewModel @AssistedInject constructor(
    @Assisted private val id: String,
    observeJournalEntry: ObserveJournalEntryUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val updateJournalEntry: UpdateJournalEntryUseCase,
    private val requestJournalStarterPrompt: RequestJournalStarterPromptUseCase,
    private val requestJournalRefinementPrompt: RequestJournalRefinementPromptUseCase,
    private val isEditable: IsEditableUseCase,
    random: Random,
) : ViewModel() {

    // State owned by this screen itself, as opposed to the entry and auth state it observes.
    private data class ScreenState(
        // Null until the user changes the text; the stored entry's text shows until then.
        val text: String? = null,
        val isSaving: Boolean = false,
        val saveError: Boolean = false,
        val isDiscardConfirmVisible: Boolean = false,
        val helpMeStart: HelpMeStartUiState = HelpMeStartUiState(),
        val helpMeRefine: HelpMeRefineUiState = HelpMeRefineUiState(),
        val pendingNavigation: EditJournalEntryNavigation? = null,
    )

    private val screenState = MutableStateFlow(ScreenState())

    private val starterPrompts = JournalStarterPromptTone.entries.map { tone ->
        JournalStarterPromptUiState(
            tone = tone,
            variant = random.nextInt(STARTER_PROMPT_VARIANT_COUNT),
        )
    }

    // filterNotNull: a missing row keeps the screen loading, as the one-shot load did.
    val uiState: StateFlow<EditJournalEntryUiState> = combine(
        observeJournalEntry(id).filterNotNull(),
        observeAuthState(),
        screenState,
    ) { entry, authState, screen ->
        EditJournalEntryUiState(
            isLoading = false,
            entry = entry.toUiState(),
            text = screen.text ?: entry.text,
            isSaving = screen.isSaving,
            saveError = screen.saveError,
            isDiscardConfirmVisible = screen.isDiscardConfirmVisible,
            authState = authState.toUiState(),
            helpMeStart = screen.helpMeStart,
            helpMeRefine = screen.helpMeRefine,
            starterPrompts = starterPrompts,
            pendingNavigation = screen.pendingNavigation,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditJournalEntryUiState(
            isLoading = true,
            entry = null,
            text = "",
            isSaving = false,
            saveError = false,
            starterPrompts = starterPrompts,
        ),
    )

    // Tracks the in-flight generate/regenerate call so a stale response can never land after the
    // sheet session it belongs to has already been reset (dismissed, reopened, or accepted).
    private var generateJob: Job? = null

    // Same purpose as generateJob, for the independent refine sheet.
    private var refineJob: Job? = null

    fun onIntent(intent: EditJournalEntryIntent) {
        when (intent) {
            is EditJournalEntryIntent.TextChanged -> onTextChanged(intent.text)
            EditJournalEntryIntent.SaveClicked -> onSaveClicked()
            EditJournalEntryIntent.BackClicked -> onBackClicked()
            EditJournalEntryIntent.DiscardConfirmed -> onDiscardConfirmed()
            EditJournalEntryIntent.NavigationHandled -> onNavigationHandled()
            EditJournalEntryIntent.DiscardDismissed -> onDiscardDismissed()
            EditJournalEntryIntent.SignInClicked -> onSignInClicked()
            EditJournalEntryIntent.HelpMeStartClicked -> onHelpMeStartClicked()
            EditJournalEntryIntent.HelpMeStartDismissed -> onHelpMeStartDismissed()
            is EditJournalEntryIntent.HelpMeStartToneSelected -> onHelpMeStartToneSelected(intent.tone)
            is EditJournalEntryIntent.HelpMeStartThoughtsChanged -> onHelpMeStartThoughtsChanged(intent.thoughts)
            EditJournalEntryIntent.GenerateClicked -> onGenerate()
            EditJournalEntryIntent.RegenerateClicked -> onGenerate()
            EditJournalEntryIntent.UseGeneratedTextClicked -> onUseGeneratedTextClicked()
            EditJournalEntryIntent.HelpMeRefineClicked -> onHelpMeRefineClicked()
            EditJournalEntryIntent.HelpMeRefineDismissed -> onHelpMeRefineDismissed()
            is EditJournalEntryIntent.HelpMeRefineToneSelected -> onHelpMeRefineToneSelected(intent.tone)
            is EditJournalEntryIntent.HelpMeRefineThoughtsChanged -> onHelpMeRefineThoughtsChanged(intent.thoughts)
            EditJournalEntryIntent.RefineClicked -> onRefine()
            EditJournalEntryIntent.RegenerateRefineClicked -> onRefine()
            EditJournalEntryIntent.UseRefinedTextClicked -> onUseRefinedTextClicked()
        }
    }

    private fun onTextChanged(text: String) {
        screenState.update { it.copy(text = text) }
    }

    private fun onSaveClicked() {
        if (screenState.value.isSaving) return
        val state = uiState.value
        val entry = state.entry ?: return
        viewModelScope.launch {
            screenState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = updateJournalEntry(entry.id, state.text)
            if (result.isSuccess) {
                screenState.update { it.copy(isSaving = false) }
                screenState.update { it.copy(pendingNavigation = EditJournalEntryNavigation.Saved) }
            } else if (result.exceptionOrNull() is EditWindowExpiredException) {
                screenState.update { it.copy(isSaving = false) }
                screenState.update { it.copy(pendingNavigation = EditJournalEntryNavigation.Discarded) }
            } else {
                screenState.update {
                    it.copy(
                        isSaving = false,
                        saveError = true,
                    )
                }
            }
        }
    }

    private fun onBackClicked() {
        val state = uiState.value
        if (state.text == state.entry?.text.orEmpty()) {
            screenState.update { it.copy(pendingNavigation = EditJournalEntryNavigation.Discarded) }
        } else {
            screenState.update { it.copy(isDiscardConfirmVisible = true) }
        }
    }

    private fun onDiscardConfirmed() {
        screenState.update { it.copy(pendingNavigation = EditJournalEntryNavigation.Discarded) }
    }

    private fun onNavigationHandled() {
        screenState.update { it.copy(pendingNavigation = null) }
    }

    private fun onDiscardDismissed() {
        screenState.update { it.copy(isDiscardConfirmVisible = false) }
    }

    private fun onHelpMeStartClicked() {
        generateJob?.cancel()
        val step = if (uiState.value.authState is AuthStateUi.SignedIn) {
            HelpMeStartStep.INPUT
        } else {
            HelpMeStartStep.SIGNED_OUT
        }
        screenState.update {
            it.copy(
                helpMeStart = it.helpMeStart.resetForNewSession(
                    isVisible = true,
                    step = step,
                ),
            )
        }
    }

    private fun onHelpMeStartDismissed() {
        generateJob?.cancel()
        screenState.update { it.copy(helpMeStart = it.helpMeStart.resetForNewSession(isVisible = false)) }
    }

    private fun onSignInClicked() {
        generateJob?.cancel()
        screenState.update { it.copy(helpMeStart = it.helpMeStart.resetForNewSession(isVisible = false)) }
        screenState.update { it.copy(pendingNavigation = EditJournalEntryNavigation.SignIn) }
    }

    private fun onHelpMeStartToneSelected(tone: JournalPromptToneUiState) {
        screenState.update { it.copy(helpMeStart = it.helpMeStart.copy(selectedTone = tone)) }
    }

    private fun onHelpMeStartThoughtsChanged(thoughts: String) {
        screenState.update { it.copy(helpMeStart = it.helpMeStart.copy(thoughts = thoughts)) }
    }

    private fun onGenerate() {
        val helpMeStart = screenState.value.helpMeStart
        val tone = helpMeStart.selectedTone ?: return
        val entry = uiState.value.entry ?: return
        if (helpMeStart.isGenerating) return
        if ((helpMeStart.remainingToday ?: 1) <= 0) return
        screenState.update {
            it.copy(
                helpMeStart = it.helpMeStart.copy(
                    isGenerating = true,
                    error = null,
                ),
            )
        }
        generateJob = viewModelScope.launch {
            val result = requestJournalStarterPrompt(tone.toDomain(), helpMeStart.thoughts.ifBlank { null })
            // Mirrors onRefine's expired-mid-flight handling: the 24h window can close during the
            // 10-20s call, and this screen has no non-editing fallback to show instead.
            if (!isEditable(entry.createdAt)) {
                screenState.update { it.copy(helpMeStart = HelpMeStartUiState()) }
                screenState.update { it.copy(pendingNavigation = EditJournalEntryNavigation.Discarded) }
                return@launch
            }
            screenState.update { current -> current.copy(helpMeStart = current.helpMeStart.applyResult(result)) }
        }
    }

    private fun onUseGeneratedTextClicked() {
        val generatedText = screenState.value.helpMeStart.generatedText ?: return
        generateJob?.cancel()
        screenState.update {
            it.copy(
                text = generatedText,
                helpMeStart = it.helpMeStart.resetForNewSession(isVisible = false),
            )
        }
    }

    private fun onHelpMeRefineClicked() {
        val state = uiState.value
        if (state.authState !is AuthStateUi.SignedIn) return
        if (state.text.isBlank() || state.text.length > MAX_REFINE_TEXT_LENGTH) return
        refineJob?.cancel()
        screenState.update { it.copy(helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = true)) }
    }

    private fun onHelpMeRefineDismissed() {
        refineJob?.cancel()
        screenState.update { it.copy(helpMeRefine = it.helpMeRefine.resetForNewSession(isVisible = false)) }
    }

    private fun onHelpMeRefineToneSelected(tone: JournalPromptToneUiState) {
        screenState.update { it.copy(helpMeRefine = it.helpMeRefine.copy(selectedTone = tone)) }
    }

    private fun onHelpMeRefineThoughtsChanged(thoughts: String) {
        screenState.update { it.copy(helpMeRefine = it.helpMeRefine.copy(thoughts = thoughts)) }
    }

    private fun onRefine() {
        val state = uiState.value
        val helpMeRefine = screenState.value.helpMeRefine
        val tone = helpMeRefine.selectedTone ?: return
        val entry = state.entry ?: return
        if (helpMeRefine.isGenerating) return
        if ((helpMeRefine.remainingToday ?: 1) <= 0) return
        screenState.update {
            it.copy(
                helpMeRefine = it.helpMeRefine.copy(
                    isGenerating = true,
                    error = null,
                ),
            )
        }
        refineJob = viewModelScope.launch {
            val result = requestJournalRefinementPrompt(
                state.text,
                tone.toDomain(),
                helpMeRefine.thoughts.ifBlank { null },
            )
            // The 24h window can close mid-request (the call takes 10-20s). Unlike the old Detail-hosted edit sheet,
            // this screen has no non-editing display mode to fall back into, so a result landing after expiry is
            // discarded the same way an explicit discard is: pop back to Detail, which observes and shows the
            // (untouched) entry read-only.
            if (!isEditable(entry.createdAt)) {
                screenState.update { it.copy(helpMeRefine = HelpMeRefineUiState()) }
                screenState.update { it.copy(pendingNavigation = EditJournalEntryNavigation.Discarded) }
                return@launch
            }
            screenState.update { current -> current.copy(helpMeRefine = current.helpMeRefine.applyResult(result)) }
        }
    }

    private fun onUseRefinedTextClicked() {
        val refinedText = screenState.value.helpMeRefine.refinedText ?: return
        refineJob?.cancel()
        screenState.update {
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

    private fun HelpMeStartUiState.applyResult(result: Result<AiPromptResult>): HelpMeStartUiState = result.fold(
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
            val error = (throwable as? AiAssistException)?.error?.toUiState() ?: AiAssistErrorUiState.UNKNOWN
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
    private fun HelpMeRefineUiState.resetForNewSession(isVisible: Boolean): HelpMeRefineUiState = copy(
        isVisible = isVisible,
        step = HelpMeRefineStep.INPUT,
        selectedTone = null,
        thoughts = "",
        refinedText = null,
        isGenerating = false,
        error = null,
    )

    private fun HelpMeRefineUiState.applyResult(result: Result<AiPromptResult>): HelpMeRefineUiState = result.fold(
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
            val error = (throwable as? AiAssistException)?.error?.toUiState() ?: AiAssistErrorUiState.UNKNOWN
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
        fun create(@Assisted id: String): EditJournalEntryViewModel
    }
}
