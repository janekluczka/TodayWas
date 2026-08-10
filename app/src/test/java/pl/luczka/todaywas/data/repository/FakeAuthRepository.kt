package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthException
import pl.luczka.todaywas.domain.model.AuthState

class FakeAuthRepository(
    initialState: AuthState = AuthState.SignedOut,
    var currentUserId: String? = null,
) : AuthRepository {

    private val state = MutableStateFlow(initialState)

    var signUpError: AuthError? = null
    var signInError: AuthError? = null
    var signInWithGoogleError: AuthError? = null
    var signOutError: AuthError? = null

    var signUpCallCount = 0
        private set
    var signInCallCount = 0
        private set
    var signOutCallCount = 0
        private set

    fun emit(newState: AuthState) {
        state.value = newState
    }

    override fun observeAuthState(): Flow<AuthState> = state

    override fun currentUserId(): String? = currentUserId

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): Result<Unit> {
        signUpCallCount++
        return signUpError?.let { Result.failure(AuthException(it)) } ?: Result.success(Unit)
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<Unit> {
        signInCallCount++
        return signInError?.let { Result.failure(AuthException(it)) } ?: Result.success(Unit)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> =
        signInWithGoogleError?.let { Result.failure(AuthException(it)) } ?: Result.success(Unit)

    override suspend fun signOut(): Result<Unit> {
        signOutCallCount++
        return signOutError?.let { Result.failure(AuthException(it)) } ?: Result.success(Unit)
    }
}
