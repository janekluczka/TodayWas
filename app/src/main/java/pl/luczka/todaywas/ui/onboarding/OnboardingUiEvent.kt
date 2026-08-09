package pl.luczka.todaywas.ui.onboarding

import pl.luczka.todaywas.ui.model.AuthErrorUiState

sealed interface OnboardingUiEvent {

    data object ExitApp : OnboardingUiEvent

    data object Finished : OnboardingUiEvent

    data class ShowError(
        val error: AuthErrorUiState,
    ) : OnboardingUiEvent
}
