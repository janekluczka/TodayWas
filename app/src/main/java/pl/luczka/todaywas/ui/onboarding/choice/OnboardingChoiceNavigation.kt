package pl.luczka.todaywas.ui.onboarding.choice

import pl.luczka.todaywas.ui.onboarding.AllSetReason

sealed interface OnboardingChoiceNavigation {

    data class Finished(
        val reason: AllSetReason,
    ) : OnboardingChoiceNavigation
}
