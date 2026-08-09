package pl.luczka.todaywas.ui.model

sealed interface AuthStateUi {

    data object Loading : AuthStateUi

    data class SignedIn(
        val email: String?,
    ) : AuthStateUi

    data object SignedOut : AuthStateUi
}
