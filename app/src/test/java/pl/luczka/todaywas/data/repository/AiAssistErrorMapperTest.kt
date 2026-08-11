package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.exceptions.UnknownRestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.AiAssistError
import java.io.IOException

class AiAssistErrorMapperTest {

    private fun fakeResponse(status: HttpStatusCode): HttpResponse = runBlocking {
        val client = HttpClient(MockEngine { respond("", status) })
        client.get("https://example.com")
    }

    @Test
    fun `should map to NotSignedIn when throwable is UnauthorizedRestException`() {
        // Arrange
        val throwable = UnauthorizedRestException("unauthorized", fakeResponse(HttpStatusCode.Unauthorized))

        // Act
        val result = throwable.toAiAssistError()

        // Assert
        assertEquals(AiAssistError.NotSignedIn, result)
    }

    @Test
    fun `should map to InvalidRequest when throwable is BadRequestRestException`() {
        // Arrange
        val throwable = BadRequestRestException("invalid_request", fakeResponse(HttpStatusCode.BadRequest))

        // Act
        val result = throwable.toAiAssistError()

        // Assert
        assertEquals(AiAssistError.InvalidRequest, result)
    }

    @Test
    fun `should map to UpstreamFailed when throwable is a RestException that is not Unauthorized or BadRequest`() {
        // Arrange
        val throwable = UnknownRestException("upstream_failed", fakeResponse(HttpStatusCode.BadGateway))

        // Act
        val result = throwable.toAiAssistError()

        // Assert
        assertEquals(AiAssistError.UpstreamFailed, result)
    }

    @Test
    fun `should map to NetworkUnavailable when throwable is an IOException`() {
        // Arrange
        val throwable = IOException("no connection")

        // Act
        val result = throwable.toAiAssistError()

        // Assert
        assertEquals(AiAssistError.NetworkUnavailable, result)
    }

    @Test
    fun `should map to Unknown when throwable is unrecognized`() {
        // Arrange
        val throwable = RuntimeException("boom")

        // Act
        val result = throwable.toAiAssistError()

        // Assert
        assertEquals(AiAssistError.Unknown, result)
    }
}
