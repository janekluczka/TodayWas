package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.auth.status.SessionStatus
import pl.luczka.todaywas.domain.model.AuthState

fun SessionStatus.toAuthState(): AuthState = when (this) {
    is SessionStatus.Initializing -> AuthState.Loading
    is SessionStatus.Authenticated -> AuthState.SignedIn(
        userId = session.user?.id.orEmpty(),
        email = session.user?.email,
    )
    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
    is SessionStatus.RefreshFailure -> AuthState.SignedOut
}
