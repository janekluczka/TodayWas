package pl.luczka.todaywas.ui.account

sealed interface AccountIntent {

    data object BackClicked : AccountIntent

    data class SignInEmailChanged(
        val value: String,
    ) : AccountIntent

    data class SignInPasswordChanged(
        val value: String,
    ) : AccountIntent

    data object SignInSubmitClicked : AccountIntent

    data class SignInGoogleIdTokenReceived(
        val idToken: String,
    ) : AccountIntent

    data object GoogleSignInFailed : AccountIntent

    data object SignUpLinkClicked : AccountIntent

    data class SignUpEmailChanged(
        val value: String,
    ) : AccountIntent

    data class SignUpPasswordChanged(
        val value: String,
    ) : AccountIntent

    data class SignUpRepeatPasswordChanged(
        val value: String,
    ) : AccountIntent

    data object SignUpSubmitClicked : AccountIntent

    data object ContinueClicked : AccountIntent

    data object SignOutClicked : AccountIntent
}
