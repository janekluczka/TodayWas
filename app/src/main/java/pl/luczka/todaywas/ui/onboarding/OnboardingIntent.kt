package pl.luczka.todaywas.ui.onboarding

import pl.luczka.todaywas.ui.model.FocusUiState

sealed interface OnboardingIntent {

    data object NextClicked : OnboardingIntent

    data object SkipClicked : OnboardingIntent

    data object StepBack : OnboardingIntent

    data class FocusOptionSelected(
        val focus: FocusUiState,
    ) : OnboardingIntent

    data object CreateAccountClicked : OnboardingIntent

    data object SignInClicked : OnboardingIntent

    data object BackToChoiceClicked : OnboardingIntent

    data class FirstNameChanged(
        val value: String,
    ) : OnboardingIntent

    data class LastNameChanged(
        val value: String,
    ) : OnboardingIntent

    data class EmailChanged(
        val value: String,
    ) : OnboardingIntent

    data class PasswordChanged(
        val value: String,
    ) : OnboardingIntent

    data object ModeToggled : OnboardingIntent

    data object SubmitClicked : OnboardingIntent

    data class GoogleIdTokenReceived(
        val idToken: String,
    ) : OnboardingIntent

    data object GoogleSignInFailed : OnboardingIntent
}
