package pl.luczka.todaywas.domain.repository

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.AuthState

interface AuthRepository {

    fun observeAuthState(): Flow<AuthState>

    fun currentUserId(): String?

    suspend fun signUpWithEmail(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit>

    suspend fun signOut(): Result<Unit>
}
