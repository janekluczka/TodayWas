package pl.luczka.todaywas.ui.onboarding

import pl.luczka.todaywas.ui.model.FocusUiState

sealed interface OnboardingIntent {

    data object NextClicked : OnboardingIntent

    data object SkipClicked : OnboardingIntent

    data object StepBack : OnboardingIntent

    data class FocusOptionSelected(
        val focus: FocusUiState,
    ) : OnboardingIntent

    data object ContinueWithoutAccountClicked : OnboardingIntent

    data object SignInSignUpClicked : OnboardingIntent

    data class SignInEmailChanged(
        val value: String,
    ) : OnboardingIntent

    data class SignInPasswordChanged(
        val value: String,
    ) : OnboardingIntent

    data object SignInSubmitClicked : OnboardingIntent

    data class SignInGoogleIdTokenReceived(
        val idToken: String,
    ) : OnboardingIntent

    data object GoogleSignInFailed : OnboardingIntent

    data object SignUpLinkClicked : OnboardingIntent

    data class SignUpEmailChanged(
        val value: String,
    ) : OnboardingIntent

    data class SignUpPasswordChanged(
        val value: String,
    ) : OnboardingIntent

    data class SignUpRepeatPasswordChanged(
        val value: String,
    ) : OnboardingIntent

    data object SignUpSubmitClicked : OnboardingIntent
}
