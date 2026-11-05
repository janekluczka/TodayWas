package pl.luczka.todaywas.core.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.core.domain.model.JournalPromptTone
import pl.luczka.todaywas.core.testing.repository.FakeAiAssistRepository

class RequestJournalRefinementPromptUseCaseTest {

    @Test
    fun `should delegate text and tone to the repository when invoked`() =
        runTest {
            // Arrange
            val repository = FakeAiAssistRepository()
            val useCase = RequestJournalRefinementPromptUseCase(repository)

            // Act
            val result = useCase(
                "Original draft text.",
                JournalPromptTone.GOOD,
                thoughts = null,
            )

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, repository.refineCallCount)
            assertEquals("Original draft text.", repository.lastRefineText)
            assertEquals(JournalPromptTone.GOOD, repository.lastRefineTone)
        }

    @Test
    fun `should delegate thoughts to the repository when provided`() =
        runTest {
            // Arrange
            val repository = FakeAiAssistRepository()
            val useCase = RequestJournalRefinementPromptUseCase(repository)

            // Act
            useCase(
                "Original draft text.",
                JournalPromptTone.GOOD,
                thoughts = "make it shorter",
            )

            // Assert
            assertEquals("make it shorter", repository.lastRefineThoughts)
        }

    @Test
    fun `should pass the failure through unchanged when the repository fails`() =
        runTest {
            // Arrange
            val repository = FakeAiAssistRepository()
            val failure = RuntimeException("upstream failed")
            repository.refineResult = Result.failure(failure)
            val useCase = RequestJournalRefinementPromptUseCase(repository)

            // Act
            val result = useCase(
                "Original draft text.",
                JournalPromptTone.BAD,
                thoughts = null,
            )

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
