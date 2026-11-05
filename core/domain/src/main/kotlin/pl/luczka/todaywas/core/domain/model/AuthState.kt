package pl.luczka.todaywas.core.domain.model

sealed interface AuthState {

    data object Loading : AuthState

    data class SignedIn(
        val userId: String,
        val email: String?,
    ) : AuthState

    data object SignedOut : AuthState
}
