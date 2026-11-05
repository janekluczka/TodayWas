package pl.luczka.todaywas.feature.onboarding.impl.choice

sealed interface OnboardingChoiceIntent {

    data object ContinueWithoutAccountClicked : OnboardingChoiceIntent

    data object NavigationHandled : OnboardingChoiceIntent
}
