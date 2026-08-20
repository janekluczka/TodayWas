package pl.luczka.todaywas.ui.onboarding.accountsetup

sealed interface OnboardingAccountSetupIntent {

    data object StepBack : OnboardingAccountSetupIntent

    data class SignInEmailChanged(
        val value: String,
    ) : OnboardingAccountSetupIntent

    data class SignInPasswordChanged(
        val value: String,
    ) : OnboardingAccountSetupIntent

    data object SignInSubmitClicked : OnboardingAccountSetupIntent

    data class SignInGoogleIdTokenReceived(
        val idToken: String,
    ) : OnboardingAccountSetupIntent

    data object GoogleSignInFailed : OnboardingAccountSetupIntent

    data object SignUpLinkClicked : OnboardingAccountSetupIntent

    data object SignInLinkClicked : OnboardingAccountSetupIntent

    data class SignUpEmailChanged(
        val value: String,
    ) : OnboardingAccountSetupIntent

    data class SignUpPasswordChanged(
        val value: String,
    ) : OnboardingAccountSetupIntent

    data class SignUpRepeatPasswordChanged(
        val value: String,
    ) : OnboardingAccountSetupIntent

    data object SignUpSubmitClicked : OnboardingAccountSetupIntent

    data class SignUpGoogleIdTokenReceived(
        val idToken: String,
    ) : OnboardingAccountSetupIntent

    data object SyncConfirmClicked : OnboardingAccountSetupIntent

    data object SyncSkipClicked : OnboardingAccountSetupIntent
}
