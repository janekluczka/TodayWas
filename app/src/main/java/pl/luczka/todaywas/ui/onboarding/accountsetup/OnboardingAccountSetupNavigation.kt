package pl.luczka.todaywas.ui.onboarding.accountsetup

import pl.luczka.todaywas.ui.onboarding.AllSetReason

sealed interface OnboardingAccountSetupNavigation {

    data object Back : OnboardingAccountSetupNavigation

    data class Finished(
        val reason: AllSetReason,
    ) : OnboardingAccountSetupNavigation
}
