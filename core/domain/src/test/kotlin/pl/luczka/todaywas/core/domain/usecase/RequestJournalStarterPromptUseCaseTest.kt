package pl.luczka.todaywas.core.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.core.domain.model.JournalPromptTone
import pl.luczka.todaywas.core.testing.repository.FakeAiAssistRepository

class RequestJournalStarterPromptUseCaseTest {

    @Test
    fun `should delegate tone and thoughts to the repository when invoked`() =
        runTest {
            // Arrange
            val repository = FakeAiAssistRepository()
            val useCase = RequestJournalStarterPromptUseCase(repository)

            // Act
            val result = useCase(JournalPromptTone.GOOD, "made progress on a hard bug")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, repository.generateCallCount)
            assertEquals(JournalPromptTone.GOOD, repository.lastTone)
            assertEquals("made progress on a hard bug", repository.lastThoughts)
        }

    @Test
    fun `should pass the failure through unchanged when the repository fails`() =
        runTest {
            // Arrange
            val repository = FakeAiAssistRepository()
            val failure = RuntimeException("upstream failed")
            repository.generateResult = Result.failure(failure)
            val useCase = RequestJournalStarterPromptUseCase(repository)

            // Act
            val result = useCase(JournalPromptTone.BAD, null)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
