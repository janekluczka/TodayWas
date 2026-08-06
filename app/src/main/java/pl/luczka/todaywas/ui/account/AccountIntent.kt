package pl.luczka.todaywas.ui.account

sealed interface AccountIntent {

    data object BackClicked : AccountIntent

    data class FirstNameChanged(
        val value: String,
    ) : AccountIntent

    data class LastNameChanged(
        val value: String,
    ) : AccountIntent

    data class EmailChanged(
        val value: String,
    ) : AccountIntent

    data class PasswordChanged(
        val value: String,
    ) : AccountIntent

    data object ModeToggled : AccountIntent

    data object SubmitClicked : AccountIntent

    data class GoogleIdTokenReceived(
        val idToken: String,
    ) : AccountIntent

    data object GoogleSignInFailed : AccountIntent

    data object SignOutClicked : AccountIntent
}
