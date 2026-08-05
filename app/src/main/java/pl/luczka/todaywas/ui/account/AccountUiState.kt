package pl.luczka.todaywas.ui.account

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.auth.AuthFormUiState
import pl.luczka.todaywas.ui.model.AuthStateUi

@Immutable
data class AccountUiState(
    val authState: AuthStateUi,
    val authForm: AuthFormUiState,
)
