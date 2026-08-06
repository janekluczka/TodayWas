package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.luczka.todaywas.domain.model.AuthException
import pl.luczka.todaywas.domain.model.AuthState
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {

    override fun observeAuthState(): Flow<AuthState> = supabase.auth.sessionStatus.map { it.toAuthState() }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): Result<Unit> = authCall {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("first_name", firstName)
                put("last_name", lastName)
            }
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<Unit> = authCall {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = authCall {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
    }

    override suspend fun signOut(): Result<Unit> = authCall { supabase.auth.signOut() }

    private suspend fun authCall(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AuthException(e.toAuthError()))
    }
}
