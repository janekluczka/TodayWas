package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.domain.model.AuthState

fun AuthState.toUiState(): AuthStateUi = when (this) {
    AuthState.Loading -> AuthStateUi.Loading
    is AuthState.SignedIn -> AuthStateUi.SignedIn(email = email)
    AuthState.SignedOut -> AuthStateUi.SignedOut
}
