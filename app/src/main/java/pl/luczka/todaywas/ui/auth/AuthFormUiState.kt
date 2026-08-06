package pl.luczka.todaywas.ui.auth

import androidx.compose.runtime.Immutable

@Immutable
data class AuthFormUiState(
    val mode: AuthFormMode = AuthFormMode.SIGN_UP,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val firstNameError: Boolean = false,
    val lastNameError: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val isSubmitting: Boolean = false,
)
