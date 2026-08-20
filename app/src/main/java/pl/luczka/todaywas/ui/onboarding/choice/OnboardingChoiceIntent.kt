package pl.luczka.todaywas.ui.onboarding.choice

sealed interface OnboardingChoiceIntent {

    data object ContinueWithoutAccountClicked : OnboardingChoiceIntent

    data object SignInSignUpClicked : OnboardingChoiceIntent
}
