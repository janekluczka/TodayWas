package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.ui.model.AuthErrorUiState

fun AuthError.toUiState(): AuthErrorUiState = when (this) {
    AuthError.EmailAlreadyRegistered -> AuthErrorUiState.EMAIL_ALREADY_REGISTERED
    AuthError.InvalidCredentials -> AuthErrorUiState.INVALID_CREDENTIALS
    AuthError.WeakPassword -> AuthErrorUiState.WEAK_PASSWORD
    AuthError.NetworkUnavailable -> AuthErrorUiState.NETWORK_UNAVAILABLE
    AuthError.Unknown -> AuthErrorUiState.UNKNOWN
}
