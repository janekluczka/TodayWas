package pl.luczka.todaywas.domain.model

sealed interface AuthError {

    data object EmailAlreadyRegistered : AuthError

    data object InvalidCredentials : AuthError

    data object WeakPassword : AuthError

    data object NetworkUnavailable : AuthError

    data object Unknown : AuthError
}

class AuthException(
    val error: AuthError,
) : Exception()
