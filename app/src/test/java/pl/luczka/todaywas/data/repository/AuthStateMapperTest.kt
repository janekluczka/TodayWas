package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.AuthState
import java.io.IOException

class AuthStateMapperTest {

    @Test
    fun `should map to Loading when status is Initializing`() {
        // Arrange
        val status = SessionStatus.Initializing

        // Act
        val result = status.toAuthState()

        // Assert
        assertEquals(AuthState.Loading, result)
    }

    @Test
    fun `should map to SignedOut when status is NotAuthenticated`() {
        // Arrange
        val status = SessionStatus.NotAuthenticated(isSignOut = false)

        // Act
        val result = status.toAuthState()

        // Assert
        assertEquals(AuthState.SignedOut, result)
    }

    @Test
    fun `should map to SignedOut when status is RefreshFailure`() {
        // Arrange
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("no connection")))

        // Act
        val result = status.toAuthState()

        // Assert
        assertEquals(AuthState.SignedOut, result)
    }

    @Test
    fun `should map to SignedIn with user id and email when status is Authenticated`() {
        // Arrange
        val session = UserSession(
            accessToken = "access",
            refreshToken = "refresh",
            expiresIn = 3600,
            tokenType = "Bearer",
            user = UserInfo(aud = "authenticated", id = "user-1", email = "person@example.com"),
        )

        // Act
        val result = SessionStatus.Authenticated(session).toAuthState()

        // Assert
        assertEquals(AuthState.SignedIn(userId = "user-1", email = "person@example.com"), result)
    }
}
