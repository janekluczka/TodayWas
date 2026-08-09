package pl.luczka.todaywas.ui.auth

import androidx.compose.runtime.Immutable

@Immutable
data class SignUpFormUiState(
    val email: String = "",
    val password: String = "",
    val repeatPassword: String = "",
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val repeatPasswordError: Boolean = false,
    val isSubmitting: Boolean = false,
)
