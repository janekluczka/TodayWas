package pl.luczka.todaywas.feature.journal.impl.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.core.domain.usecase.AddJournalEntryUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.core.ui.mapper.toDomain
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.core.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.domain.aiassist.usecase.RequestJournalRefinementPromptUseCase
import pl.luczka.todaywas.domain.aiassist.usecase.RequestJournalStarterPromptUseCase
import pl.luczka.todaywas.domain.auth.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.feature.journal.impl.edit.HelpMeRefineUiState
import pl.luczka.todaywas.feature.journal.impl.edit.MAX_REFINE_TEXT_LENGTH
import pl.luczka.todaywas.feature.journal.impl.edit.applyResult
import pl.luczka.todaywas.feature.journal.impl.edit.requestStarted
import pl.luczka.todaywas.feature.journal.impl.edit.requestableTone
import pl.luczka.todaywas.feature.journal.impl.edit.resetForNewSession
import pl.luczka.todaywas.feature.journal.impl.mapper.toDomain
import pl.luczka.todaywas.feature.journal.impl.model.JournalPromptToneUiState

@HiltViewModel
internal class AddJournalEntryViewModel @Inject constructor(
    observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val addJournalEntry: AddJournalEntryUseCase,
    private val requestJournalStarterPrompt: RequestJournalStarterPromptUseCase,
    private val requestJournalRefinementPrompt: RequestJournalRefinementPromptUseCase,
    random: Random,
) : ViewModel() {

    // State owned by this screen itself, as opposed to the slots and auth state it observes.
    private data class ScreenState(
        val selectedSlot: JournalDateSlotUiState = JournalDateSlotUiState.TODAY,
        val text: String = "",
        val isSaving: Boolean = false,
        val saveError: Boolean = false,
        val helpMeStart: HelpMeStartUiState = HelpMeStartUiState(),
        val helpMeRefine: HelpMeRefineUiState = HelpMeRefineUiState(),
        val pendingNavigation: AddJournalEntryNavigation? = null,
    )

    private val screenState = MutableStateFlow(ScreenState())

    private val starterPrompts = JournalStarterPromptTone.entries.map { tone ->
        JournalStarterPromptUiState(
            tone = tone,
            variant = random.nextInt(STARTER_PROMPT_VARIANT_COUNT),
        )
    }

    val uiState: StateFlow<AddJournalEntryUiState> = combine(
        observeAddableJournalDateSlots(),
        observeAuthState(),
        screenState,
    ) { slots, authState, screen ->
        val availableSlots = slots.map { it.toUiState() }
        // A picked slot that is no longer addable falls back to the first one that is.
        val selectedSlot = if (screen.selectedSlot in availableSlots) {
            screen.selectedSlot
        } else {
            availableSlots.firstOrNull() ?: screen.selectedSlot
        }
        AddJournalEntryUiState(
            availableSlots = availableSlots,
            selectedSlot = selectedSlot,
            text = screen.text,
            isSaving = screen.isSaving,
            saveError = screen.saveError,
            authState = authState.toUiState(),
            helpMeStart = screen.helpMeStart,
            helpMeRefine = screen.helpMeRefine,
            starterPrompts = starterPrompts,
            pendingNavigation = screen.pendingNavigation,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddJournalEntryUiState(
            availableSlots = emptyList(),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "",
            isSaving = false,
            saveError = false,
            starterPrompts = starterPrompts,
        ),
    )

    // Tracks the in-flight generate/regenerate call so a stale response can never land after the
    // dialog session it belongs to has already been reset (dismissed, reopened, or accepted).
    private var generateJob: Job? = null

    // Same purpose as generateJob, for the independent refine sheet.
    private var refineJob: Job? = null

    fun onIntent(intent: AddJournalEntryIntent) {
        when (intent) {
            is AddJournalEntryIntent.SlotSelected -> onSlotSelected(intent.slot)
            is AddJournalEntryIntent.TextChanged -> onTextChanged(intent.text)
            AddJournalEntryIntent.SaveClicked -> onSaveClicked()
            AddJournalEntryIntent.NavigationHandled -> onNavigationHandled()
            AddJournalEntryIntent.HelpMeStartClicked -> onHelpMeStartClicked()
            AddJournalEntryIntent.HelpMeStartDismissed -> onHelpMeStartDismissed()
            AddJournalEntryIntent.SignInClicked -> onSignInClicked()
            is AddJournalEntryIntent.HelpMeStartToneSelected -> onHelpMeStartToneSelected(intent.tone)
            is AddJournalEntryIntent.HelpMeStartThoughtsChanged -> onHelpMeStartThoughtsChanged(intent.thoughts)
            AddJournalEntryIntent.GenerateClicked -> onGenerate()
            AddJournalEntryIntent.RegenerateClicked -> onGenerate()
            AddJournalEntryIntent.UseGeneratedTextClicked -> onUseGeneratedTextClicked()
            AddJournalEntryIntent.HelpMeRefineClicked -> onHelpMeRefineClicked()
            AddJournalEntryIntent.HelpMeRefineDismissed -> onHelpMeRefineDismissed()
            is AddJournalEntryIntent.HelpMeRefineToneSelected -> onHelpMeRefineToneSelected(intent.tone)
            is AddJournalEntryIntent.HelpMeRefineThoughtsChanged -> onHelpMeRefineThoughtsChanged(intent.thoughts)
            AddJournalEntryIntent.RefineClicked -> onRefine()
            AddJournalEntryIntent.RegenerateRefineClicked -> onRefine()
            AddJournalEntryIntent.UseRefinedTextClicked -> onUseRefinedTextClicked()
        }
    }

    private fun onSlotSelected(slot: JournalDateSlotUiState) {
        screenState.update { it.copy(selectedSlot = slot) }
    }

    private fun onTextChanged(text: String) {
        screenState.update { it.copy(text = text) }
    }

    private fun onSaveClicked() {
        if (screenState.value.isSaving) return
        val state = uiState.value
        viewModelScope.launch {
            screenState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = addJournalEntry(state.selectedSlot.toDomain(), state.text)
            if (result.isSuccess) {
                screenState.update {
                    it.copy(
                        isSaving = false,
                        pendingNavigation = AddJournalEntryNavigation.Saved,
                    )
                }
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

    private fun onNavigationHandled() {
        screenState.update { it.copy(pendingNavigation = null) }
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
        screenState.update { it.copy(pendingNavigation = AddJournalEntryNavigation.SignIn) }
    }

    private fun onHelpMeStartToneSelected(tone: JournalPromptToneUiState) {
        screenState.update { it.copy(helpMeStart = it.helpMeStart.copy(selectedTone = tone)) }
    }

    private fun onHelpMeStartThoughtsChanged(thoughts: String) {
        screenState.update { it.copy(helpMeStart = it.helpMeStart.copy(thoughts = thoughts)) }
    }

    private fun onGenerate() {
        val helpMeStart = screenState.value.helpMeStart
        val tone = helpMeStart.requestableTone() ?: return
        screenState.update { it.copy(helpMeStart = it.helpMeStart.requestStarted()) }
        generateJob = viewModelScope.launch {
            val result = requestJournalStarterPrompt(tone.toDomain(), helpMeStart.thoughts.ifBlank { null })
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
        val helpMeRefine = screenState.value.helpMeRefine
        val tone = helpMeRefine.requestableTone() ?: return
        screenState.update { it.copy(helpMeRefine = it.helpMeRefine.requestStarted()) }
        refineJob = viewModelScope.launch {
            val result = requestJournalRefinementPrompt(
                screenState.value.text,
                tone.toDomain(),
                helpMeRefine.thoughts.ifBlank { null },
            )
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
}
