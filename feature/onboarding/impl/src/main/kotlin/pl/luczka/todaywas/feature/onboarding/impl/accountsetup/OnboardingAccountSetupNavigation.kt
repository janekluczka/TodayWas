package pl.luczka.todaywas.feature.onboarding.impl.accountsetup

import pl.luczka.todaywas.feature.onboarding.api.navigation.AllSetReason

internal sealed interface OnboardingAccountSetupNavigation {

    data object Back : OnboardingAccountSetupNavigation

    data class Finished(
        val reason: AllSetReason,
    ) : OnboardingAccountSetupNavigation
}
