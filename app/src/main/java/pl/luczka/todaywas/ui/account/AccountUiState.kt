package pl.luczka.todaywas.ui.account

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.model.AuthStateUi

@Immutable
data class AccountUiState(
    val authState: AuthStateUi,
    val step: AccountStep,
    val signInForm: SignInFormUiState,
    val signUpForm: SignUpFormUiState,
)
