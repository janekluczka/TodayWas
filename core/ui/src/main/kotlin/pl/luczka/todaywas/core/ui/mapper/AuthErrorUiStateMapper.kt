package pl.luczka.todaywas.core.ui.mapper

import pl.luczka.todaywas.core.domain.model.AuthError
import pl.luczka.todaywas.core.ui.model.AuthErrorUiState

fun AuthError.toUiState(): AuthErrorUiState = when (this) {
    AuthError.EmailAlreadyRegistered -> AuthErrorUiState.EMAIL_ALREADY_REGISTERED
    AuthError.InvalidCredentials -> AuthErrorUiState.INVALID_CREDENTIALS
    AuthError.WeakPassword -> AuthErrorUiState.WEAK_PASSWORD
    AuthError.NetworkUnavailable -> AuthErrorUiState.NETWORK_UNAVAILABLE
    AuthError.Unknown -> AuthErrorUiState.UNKNOWN
}
