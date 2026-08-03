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
    fun `Initializing maps to Loading`() {
        assertEquals(AuthState.Loading, SessionStatus.Initializing.toAuthState())
    }

    @Test
    fun `NotAuthenticated maps to SignedOut`() {
        assertEquals(AuthState.SignedOut, SessionStatus.NotAuthenticated(isSignOut = false).toAuthState())
    }

    @Test
    fun `RefreshFailure maps to SignedOut`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("no connection")))
        assertEquals(AuthState.SignedOut, status.toAuthState())
    }

    @Test
    fun `Authenticated maps to SignedIn with user id and email`() {
        val session = UserSession(
            accessToken = "access",
            refreshToken = "refresh",
            expiresIn = 3600,
            tokenType = "Bearer",
            user = UserInfo(aud = "authenticated", id = "user-1", email = "person@example.com"),
        )

        val result = SessionStatus.Authenticated(session).toAuthState()

        assertEquals(AuthState.SignedIn(userId = "user-1", email = "person@example.com"), result)
    }
}
