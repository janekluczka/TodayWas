package pl.luczka.todaywas.ui.onboarding.choice

import pl.luczka.todaywas.ui.onboarding.AllSetReason

sealed interface OnboardingChoiceUiEvent {

    data object NavigateToAccountSetup : OnboardingChoiceUiEvent

    data class Finished(
        val reason: AllSetReason,
    ) : OnboardingChoiceUiEvent
}
