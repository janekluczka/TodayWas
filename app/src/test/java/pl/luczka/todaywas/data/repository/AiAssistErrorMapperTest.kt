package pl.luczka.todaywas.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.AiAssistError
import java.io.IOException

class AiAssistErrorMapperTest {

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
