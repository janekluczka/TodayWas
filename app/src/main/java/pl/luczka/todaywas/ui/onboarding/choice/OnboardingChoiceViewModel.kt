package pl.luczka.todaywas.ui.onboarding.choice

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
import pl.luczka.todaywas.domain.usecase.CompleteOnboardingUseCase
import pl.luczka.todaywas.ui.onboarding.AllSetReason
import javax.inject.Inject

@HiltViewModel
class OnboardingChoiceViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingChoiceUiState())
    val uiState: StateFlow<OnboardingChoiceUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<OnboardingChoiceUiEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingChoiceUiEvent> = eventChannel.receiveAsFlow()

    fun onIntent(intent: OnboardingChoiceIntent) {
        when (intent) {
            OnboardingChoiceIntent.ContinueWithoutAccountClicked ->
                onContinueWithoutAccountClicked()
            OnboardingChoiceIntent.SignInSignUpClicked ->
                eventChannel.trySend(OnboardingChoiceUiEvent.NavigateToAccountSetup)
        }
    }

    private fun onContinueWithoutAccountClicked() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = false) }
            val result = completeOnboarding()
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                eventChannel.trySend(OnboardingChoiceUiEvent.Finished(AllSetReason.NO_ACCOUNT))
            } else {
                _uiState.update { it.copy(isSaving = false, saveError = true) }
            }
        }
    }
}
