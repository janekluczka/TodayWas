package pl.luczka.todaywas.ui.onboarding.accountsetup

import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.onboarding.AllSetReason

sealed interface OnboardingAccountSetupUiEvent {

    data object NavigateBack : OnboardingAccountSetupUiEvent

    data class Finished(
        val reason: AllSetReason,
    ) : OnboardingAccountSetupUiEvent

    data class ShowError(
        val error: AuthErrorUiState,
    ) : OnboardingAccountSetupUiEvent
}
