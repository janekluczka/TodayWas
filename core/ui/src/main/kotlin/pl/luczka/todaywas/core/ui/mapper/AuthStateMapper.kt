package pl.luczka.todaywas.core.ui.mapper

import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.domain.auth.model.AuthState

fun AuthState.toUiState(): AuthStateUi = when (this) {
    AuthState.Loading -> AuthStateUi.Loading
    is AuthState.SignedIn -> AuthStateUi.SignedIn(email = email)
    AuthState.SignedOut -> AuthStateUi.SignedOut
}
