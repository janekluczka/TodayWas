package pl.luczka.todaywas.ui.auth.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.luczka.todaywas.R
import pl.luczka.todaywas.ui.model.AuthErrorUiState

fun isValidEmail(email: String): Boolean = email.isNotBlank() && email.contains("@")

fun isValidPassword(password: String): Boolean = password.length >= 6

fun isValidRepeatPassword(
    password: String,
    repeatPassword: String,
): Boolean = repeatPassword == password

// Shared with any screen surfacing auth errors (Account, onboarding) so the same
// AuthErrorUiState -> copy mapping isn't duplicated per screen.
@Composable
fun AuthErrorUiState.message(): String = when (this) {
    AuthErrorUiState.EMAIL_ALREADY_REGISTERED -> stringResource(
        R.string.auth_error_email_already_registered,
    )
    AuthErrorUiState.INVALID_CREDENTIALS -> stringResource(R.string.auth_error_invalid_credentials)
    AuthErrorUiState.WEAK_PASSWORD -> stringResource(R.string.auth_error_weak_password)
    AuthErrorUiState.NETWORK_UNAVAILABLE -> stringResource(R.string.auth_error_network_unavailable)
    AuthErrorUiState.UNKNOWN -> stringResource(R.string.auth_error_unknown)
}
