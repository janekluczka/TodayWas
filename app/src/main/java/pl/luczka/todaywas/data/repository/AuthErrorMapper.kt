package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import pl.luczka.todaywas.domain.model.AuthError
import java.io.IOException

// HttpRequestException (thrown for connectivity failures) is itself an IOException subclass,
// so a single IOException branch covers both.
fun Throwable.toAuthError(): AuthError = when (this) {
    is AuthRestException -> errorCode.toAuthError()
    is IOException -> AuthError.NetworkUnavailable
    else -> AuthError.Unknown
}

fun AuthErrorCode?.toAuthError(): AuthError = when (this) {
    AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> AuthError.EmailAlreadyRegistered
    AuthErrorCode.InvalidCredentials -> AuthError.InvalidCredentials
    AuthErrorCode.WeakPassword -> AuthError.WeakPassword
    else -> AuthError.Unknown
}
