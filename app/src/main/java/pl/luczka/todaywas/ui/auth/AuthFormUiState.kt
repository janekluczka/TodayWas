package pl.luczka.todaywas.ui.auth

import androidx.compose.runtime.Immutable

@Immutable
data class AuthFormUiState(
    val mode: AuthFormMode = AuthFormMode.SIGN_UP,
    val email: String = "",
    val password: String = "",
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val isSubmitting: Boolean = false,
)
