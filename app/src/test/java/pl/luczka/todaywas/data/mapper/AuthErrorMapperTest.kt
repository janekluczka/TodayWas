package pl.luczka.todaywas.data.mapper

import io.github.jan.supabase.auth.exception.AuthErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.AuthError
import java.io.IOException

class AuthErrorMapperTest {

    @Test
    fun `should map to EmailAlreadyRegistered when code is EmailExists or UserAlreadyExists`() {
        // Arrange
        val emailExists = AuthErrorCode.EmailExists
        val userAlreadyExists = AuthErrorCode.UserAlreadyExists

        // Act
        val fromEmailExists = emailExists.toAuthError()
        val fromUserAlreadyExists = userAlreadyExists.toAuthError()

        // Assert
        assertEquals(AuthError.EmailAlreadyRegistered, fromEmailExists)
        assertEquals(AuthError.EmailAlreadyRegistered, fromUserAlreadyExists)
    }

    @Test
    fun `should map to InvalidCredentials when code is InvalidCredentials`() {
        // Arrange
        val code = AuthErrorCode.InvalidCredentials

        // Act
        val result = code.toAuthError()

        // Assert
        assertEquals(AuthError.InvalidCredentials, result)
    }

    @Test
    fun `should map to WeakPassword when code is WeakPassword`() {
        // Arrange
        val code = AuthErrorCode.WeakPassword

        // Act
        val result = code.toAuthError()

        // Assert
        assertEquals(AuthError.WeakPassword, result)
    }

    @Test
    fun `should map to Unknown when code is unrecognized`() {
        // Arrange
        val code = AuthErrorCode.BadJwt

        // Act
        val result = code.toAuthError()

        // Assert
        assertEquals(AuthError.Unknown, result)
    }

    @Test
    fun `should map to Unknown when code is null`() {
        // Arrange
        val code: AuthErrorCode? = null

        // Act
        val result = code.toAuthError()

        // Assert
        assertEquals(AuthError.Unknown, result)
    }

    @Test
    fun `should map to NetworkUnavailable when throwable is an IOException`() {
        // Arrange
        val throwable = IOException("no connection")

        // Act
        val result = throwable.toAuthError()

        // Assert
        assertEquals(AuthError.NetworkUnavailable, result)
    }

    @Test
    fun `should map to Unknown when throwable is unrecognized`() {
        // Arrange
        val throwable = RuntimeException("boom")

        // Act
        val result = throwable.toAuthError()

        // Assert
        assertEquals(AuthError.Unknown, result)
    }
}
