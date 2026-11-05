package pl.luczka.todaywas.feature.onboarding.impl.choice

import pl.luczka.todaywas.feature.onboarding.api.navigation.AllSetReason

internal sealed interface OnboardingChoiceNavigation {

    data class Finished(
        val reason: AllSetReason,
    ) : OnboardingChoiceNavigation
}
