package pl.luczka.todaywas.ui.onboarding.choice

import pl.luczka.todaywas.feature.onboarding.api.navigation.AllSetReason

sealed interface OnboardingChoiceNavigation {

    data class Finished(
        val reason: AllSetReason,
    ) : OnboardingChoiceNavigation
}
