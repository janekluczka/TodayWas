package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.AiAssistError
import pl.luczka.todaywas.domain.model.AiAssistException
import pl.luczka.todaywas.domain.model.JournalPromptTone

class AiAssistRepositoryImplTest {

    private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = AiAssistRepositoryImpl(
        createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "test-key",
        ) {
            httpEngine = MockEngine(handler)
            install(Functions)
        },
    )

    @Test
    fun `should return the generated text when generateJournalStarterPrompt succeeds`() =
        runTest {
            // Arrange
            val repository = repository {
                respond(
                    """{"text":"Generated prompt"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            // Act
            val result = repository.generateJournalStarterPrompt(
                JournalPromptTone.GOOD,
                thoughts = null,
            )

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("Generated prompt", result.getOrNull())
        }

    @Test
    fun `should return AiAssistException with the mapped error when the proxy call fails`() =
        runTest {
            // Arrange
            val repository = repository { respond("upstream failed", HttpStatusCode.BadGateway) }

            // Act
            val result = repository.generateJournalStarterPrompt(
                JournalPromptTone.GOOD,
                thoughts = null,
            )

            // Assert
            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as? AiAssistException)?.error
            assertEquals(AiAssistError.UpstreamFailed, error)
        }

    @Test
    fun `should return the refined text when refineJournalEntry succeeds`() =
        runTest {
            // Arrange
            val repository = repository {
                respond(
                    """{"text":"Refined text"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            // Act
            val result = repository.refineJournalEntry("Original text", JournalPromptTone.NEUTRAL)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("Refined text", result.getOrNull())
        }

    @Test
    fun `should configure a 30s request and socket timeout override`() =
        runTest {
            // Arrange
            var capturedRequestTimeoutMillis: Long? = null
            var capturedSocketTimeoutMillis: Long? = null
            val repository = repository { request ->
                val timeoutConfig = request.getCapabilityOrNull(HttpTimeoutCapability)
                capturedRequestTimeoutMillis = timeoutConfig?.requestTimeoutMillis
                capturedSocketTimeoutMillis = timeoutConfig?.socketTimeoutMillis
                respond(
                    """{"text":"ok"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            // Act
            repository.generateJournalStarterPrompt(JournalPromptTone.GOOD, thoughts = null)

            // Assert
            assertEquals(30_000L, capturedRequestTimeoutMillis)
            assertEquals(30_000L, capturedSocketTimeoutMillis)
        }

    @Test
    fun `should propagate CancellationException rather than converting it to a failure`() =
        runTest {
            // Arrange — the engine suspends mid-call so the cancellation below lands inside
            // invokeAiProxy's try block, not before the coroutine even starts.
            val repository = repository {
                delay(50)
                respond(
                    """{"text":"ok"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            var caught: Throwable? = null

            // Act
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    repository.generateJournalStarterPrompt(JournalPromptTone.GOOD, thoughts = null)
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
