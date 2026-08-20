package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthException

private const val SUCCESS_SESSION_BODY = """
{
    "access_token": "test-access-token",
    "refresh_token": "test-refresh-token",
    "expires_in": 3600,
    "token_type": "bearer",
    "user": { "aud": "authenticated", "id": "test-user-id" }
}
"""

private fun errorBody(errorCode: String) = """{"error_code":"$errorCode","error_description":"failed"}"""

class AuthRepositoryImplTest {

    private fun repository(engine: MockEngine) = AuthRepositoryImpl(
        createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "test-key",
        ) {
            httpEngine = engine
            install(Auth) { minimalConfig() }
        },
    )

    @Test
    fun `should return success when signInWithEmail succeeds`() =
        runTest {
            // Arrange
            val engine =
                MockEngine {
                    respond(
                        SUCCESS_SESSION_BODY,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repository = repository(engine)

            // Act
            val result = repository.signInWithEmail("person@example.com", "password123")

            // Assert
            assertTrue(result.isSuccess)
        }

    @Test
    fun `should return AuthException with the mapped error when signInWithEmail's response is an error status`() =
        runTest {
            // Arrange
            val engine = MockEngine {
                respond(
                    errorBody("invalid_credentials"),
                    HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val repository = repository(engine)

            // Act
            val result = repository.signInWithEmail("person@example.com", "wrong-password")

            // Assert
            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as? AuthException)?.error
            assertEquals(AuthError.InvalidCredentials, error)
        }

    @Test
    fun `should return success when signUpWithEmail succeeds`() =
        runTest {
            // Arrange
            val engine =
                MockEngine {
                    respond(
                        SUCCESS_SESSION_BODY,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repository = repository(engine)

            // Act
            val result = repository.signUpWithEmail("person@example.com", "password123")

            // Assert
            assertTrue(result.isSuccess)
        }

    @Test
    fun `should return AuthException with the mapped error when signUpWithEmail's response is an error status`() =
        runTest {
            // Arrange
            val engine = MockEngine {
                respond(
                    errorBody("email_exists"),
                    HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val repository = repository(engine)

            // Act
            val result = repository.signUpWithEmail("person@example.com", "password123")

            // Assert
            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as? AuthException)?.error
            assertEquals(AuthError.EmailAlreadyRegistered, error)
        }

    @Test
    fun `should return success without a network call when signOut is called with no active session`() =
        runTest {
            // Arrange
            val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
            val repository = repository(engine)

            // Act
            val result = repository.signOut()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(0, engine.requestHistory.size)
        }

    @Test
    fun `should return null from currentUserId when signed out`() {
        // Arrange
        val repository = repository(MockEngine { respond("", HttpStatusCode.OK) })

        // Act
        val userId = repository.currentUserId()

        // Assert
        assertNull(userId)
    }

    @Test
    fun `should propagate CancellationException rather than converting it to a failure`() =
        runTest {
            // Arrange — the engine suspends mid-call so the cancellation below lands inside
            // authCall's try block, not before the coroutine even starts.
            val engine = MockEngine {
                delay(50)
                respond(
                    SUCCESS_SESSION_BODY,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val repository = repository(engine)
            var caught: Throwable? = null

            // Act
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    repository.signInWithEmail("person@example.com", "password123")
                } catch (e: CancellationException) {
                    caught = e
                    throw e
                }
            }
            job.cancel()
            job.join()

            // Assert
            assertTrue(caught is CancellationException)
        }
}
