package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.auth.exception.AuthErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.AuthError
import java.io.IOException

class AuthErrorMapperTest {

    @Test
    fun `email exists and user already exists codes map to EmailAlreadyRegistered`() {
        assertEquals(AuthError.EmailAlreadyRegistered, AuthErrorCode.EmailExists.toAuthError())
        assertEquals(AuthError.EmailAlreadyRegistered, AuthErrorCode.UserAlreadyExists.toAuthError())
    }

    @Test
    fun `invalid credentials code maps to InvalidCredentials`() {
        assertEquals(AuthError.InvalidCredentials, AuthErrorCode.InvalidCredentials.toAuthError())
    }

    @Test
    fun `weak password code maps to WeakPassword`() {
        assertEquals(AuthError.WeakPassword, AuthErrorCode.WeakPassword.toAuthError())
    }

    @Test
    fun `unrecognized code maps to Unknown`() {
        assertEquals(AuthError.Unknown, AuthErrorCode.BadJwt.toAuthError())
    }

    @Test
    fun `null code maps to Unknown`() {
        assertEquals(AuthError.Unknown, null.toAuthError())
    }

    @Test
    fun `IOException maps to NetworkUnavailable`() {
        assertEquals(AuthError.NetworkUnavailable, IOException("no connection").toAuthError())
    }

    @Test
    fun `unrecognized throwable maps to Unknown`() {
        assertEquals(AuthError.Unknown, RuntimeException("boom").toAuthError())
    }
}
