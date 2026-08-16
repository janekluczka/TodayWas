package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.ui.model.AuthStateUi

fun AuthState.toUiState(): AuthStateUi = when (this) {
    AuthState.Loading -> AuthStateUi.Loading
    is AuthState.SignedIn -> AuthStateUi.SignedIn(email = email)
    AuthState.SignedOut -> AuthStateUi.SignedOut
}
