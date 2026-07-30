package pl.luczka.todaywas.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.usecase.SelectFocusUseCase
import pl.luczka.todaywas.domain.usecase.SkipOnboardingUseCase
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.toDomain
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val selectFocus: SelectFocusUseCase,
    private val skipOnboarding: SkipOnboardingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = OnboardingStep.WELCOME,
            selectedFocus = null,
            confirmedFocus = null,
            isSaving = false,
            saveError = false,
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<OnboardingUiEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingUiEvent> = eventChannel.receiveAsFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.NextClicked -> onNextClicked()
            OnboardingIntent.SkipClicked -> onSkipClicked()
            OnboardingIntent.StepBack -> onStepBack()
            is OnboardingIntent.FocusOptionSelected -> onFocusOptionSelected(intent.focus)
            // Real account creation is F-01's job, deferred.
            OnboardingIntent.CreateAccountClicked -> Unit
        }
    }

    private fun onNextClicked() {
        when (_uiState.value.step) {
            OnboardingStep.WELCOME -> _uiState.update { it.copy(step = OnboardingStep.FOCUS_PICK) }
            OnboardingStep.FOCUS_PICK -> onConfirmFocus()
            OnboardingStep.ACCOUNT_INFO -> _uiState.update { it.copy(step = OnboardingStep.ALL_SET) }
            OnboardingStep.ALL_SET -> eventChannel.trySend(OnboardingUiEvent.Finished)
        }
    }

    private fun onConfirmFocus() {
        if (_uiState.value.isSaving) return
        val focus = _uiState.value.selectedFocus ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = selectFocus(focus.toDomain())
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        isSaving = false,
                        confirmedFocus = focus,
                        step = OnboardingStep.ACCOUNT_INFO,
                    )
                } else {
                    current.copy(
                        isSaving = false,
                        saveError = true,
                    )
                }
            }
        }
    }

    private fun onFocusOptionSelected(focus: FocusUiState) {
        _uiState.update { it.copy(selectedFocus = focus) }
    }

    private fun onStepBack() {
        when (_uiState.value.step) {
            OnboardingStep.FOCUS_PICK -> _uiState.update { it.copy(step = OnboardingStep.WELCOME) }
            OnboardingStep.ACCOUNT_INFO ->
                _uiState.update {
                    it.copy(
                        step = OnboardingStep.FOCUS_PICK,
                        selectedFocus = it.confirmedFocus,
                    )
                }
            OnboardingStep.ALL_SET -> _uiState.update { it.copy(step = OnboardingStep.ACCOUNT_INFO) }
            OnboardingStep.WELCOME -> eventChannel.trySend(OnboardingUiEvent.ExitApp)
        }
    }

    private fun onSkipClicked() {
        if (_uiState.value.isSaving) return
        if (_uiState.value.confirmedFocus != null) {
            eventChannel.trySend(OnboardingUiEvent.Finished)
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = skipOnboarding()
            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveError = result.isFailure,
                )
            }
            if (result.isSuccess) {
                eventChannel.trySend(OnboardingUiEvent.Finished)
            }
        }
    }
}
