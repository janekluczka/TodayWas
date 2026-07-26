package pl.luczka.todaywas.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.domain.usecase.SelectFocusUseCase
import pl.luczka.todaywas.domain.usecase.SkipOnboardingUseCase
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeOnboardingState: ObserveOnboardingStateUseCase,
    private val selectFocus: SelectFocusUseCase,
    private val skipOnboarding: SkipOnboardingUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            MainUiState(
                currentFocus = null,
                showOnboardingDialog = false,
                onboardingMode = OnboardingMode.MANDATORY,
                onboardingStep = OnboardingStep.WELCOME,
                selectedFocusInDialog = null,
                isSaving = false,
                saveError = false,
            ),
        )
    val uiState: StateFlow<MainUiState> = _uiState

    private val eventChannel = Channel<MainUiEvent>(Channel.BUFFERED)
    val events: Flow<MainUiEvent> = eventChannel.receiveAsFlow()

    // Guards the one-time dialog/step decision so later re-emissions of the same flow
    // (e.g. after a focus is saved) only refresh currentFocus, not the in-progress step.
    private var onboardingStateInitialized = false

    init {
        viewModelScope.launch {
            observeOnboardingState().collect { state ->
                _uiState.update { current ->
                    if (!onboardingStateInitialized) {
                        onboardingStateInitialized = true
                        current.copy(
                            currentFocus = state.focus,
                            showOnboardingDialog = !state.completed,
                            onboardingMode = OnboardingMode.MANDATORY,
                            onboardingStep = OnboardingStep.WELCOME,
                        )
                    } else {
                        current.copy(currentFocus = state.focus)
                    }
                }
            }
        }
    }

    fun onIntent(intent: MainIntent) {
        when (intent) {
            MainIntent.WelcomeContinue -> onWelcomeContinue()
            is MainIntent.FocusOptionSelected -> onFocusOptionSelected(intent.focus)
            MainIntent.ConfirmSelection -> onConfirmSelection()
            MainIntent.CreateAccountClicked -> onCreateAccountClicked()
            MainIntent.AccountContinue -> onAccountContinue()
            MainIntent.FinishOnboarding -> onFinishOnboarding()
            MainIntent.StepBack -> onStepBack()
            MainIntent.SkipOnboarding -> onSkipOnboarding()
            MainIntent.ChangeFocusRequested -> onChangeFocusRequested()
            MainIntent.RetrySave -> onRetrySave()
        }
    }

    private fun onWelcomeContinue() {
        _uiState.update { it.copy(onboardingStep = OnboardingStep.FOCUS_PICK) }
    }

    private fun onFocusOptionSelected(focus: Focus) {
        _uiState.update { it.copy(selectedFocusInDialog = focus) }
    }

    private fun onConfirmSelection() {
        val focus = _uiState.value.selectedFocusInDialog ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = false) }
            val result = selectFocus(focus)
            _uiState.update { current ->
                if (result.isSuccess) {
                    when (current.onboardingMode) {
                        OnboardingMode.MANDATORY ->
                            current.copy(
                                currentFocus = focus,
                                isSaving = false,
                                onboardingStep = OnboardingStep.ACCOUNT_INFO,
                            )
                        OnboardingMode.REPICK ->
                            current.copy(
                                currentFocus = focus,
                                isSaving = false,
                                showOnboardingDialog = false,
                            )
                    }
                } else {
                    current.copy(isSaving = false, saveError = true)
                }
            }
        }
    }

    private fun onCreateAccountClicked() {
        // Intentional no-op — real account creation is F-01's job, deferred.
    }

    private fun onAccountContinue() {
        _uiState.update { it.copy(onboardingStep = OnboardingStep.ALL_SET) }
    }

    private fun onFinishOnboarding() {
        _uiState.update { it.copy(showOnboardingDialog = false) }
    }

    private fun onStepBack() {
        when (_uiState.value.onboardingStep) {
            OnboardingStep.FOCUS_PICK ->
                _uiState.update { it.copy(onboardingStep = OnboardingStep.WELCOME) }
            OnboardingStep.ACCOUNT_INFO ->
                _uiState.update {
                    it.copy(
                        onboardingStep = OnboardingStep.FOCUS_PICK,
                        selectedFocusInDialog = it.currentFocus,
                    )
                }
            OnboardingStep.ALL_SET ->
                _uiState.update { it.copy(onboardingStep = OnboardingStep.ACCOUNT_INFO) }
            OnboardingStep.WELCOME -> eventChannel.trySend(MainUiEvent.ExitApp)
        }
    }

    private fun onSkipOnboarding() {
        if (_uiState.value.currentFocus == null) {
            viewModelScope.launch {
                skipOnboarding()
                _uiState.update { it.copy(currentFocus = Focus.BOTH, showOnboardingDialog = false) }
            }
        } else {
            _uiState.update { it.copy(showOnboardingDialog = false) }
        }
    }

    private fun onChangeFocusRequested() {
        _uiState.update {
            it.copy(
                showOnboardingDialog = true,
                onboardingMode = OnboardingMode.REPICK,
                onboardingStep = OnboardingStep.FOCUS_PICK,
                selectedFocusInDialog = it.currentFocus,
            )
        }
    }

    private fun onRetrySave() {
        onConfirmSelection()
    }
}
