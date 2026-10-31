package pl.luczka.todaywas.ui.onboarding.allset

sealed interface OnboardingAllSetIntent {

    data object GetStartedClicked : OnboardingAllSetIntent

    data object NavigationHandled : OnboardingAllSetIntent
}
