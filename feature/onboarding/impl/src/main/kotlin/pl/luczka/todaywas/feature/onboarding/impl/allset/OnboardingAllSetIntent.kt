package pl.luczka.todaywas.feature.onboarding.impl.allset

sealed interface OnboardingAllSetIntent {

    data object GetStartedClicked : OnboardingAllSetIntent

    data object NavigationHandled : OnboardingAllSetIntent
}
