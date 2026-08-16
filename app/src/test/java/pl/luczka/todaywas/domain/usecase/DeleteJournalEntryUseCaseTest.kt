package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.repository.FakeJournalRepository

class DeleteJournalEntryUseCaseTest {

    @Test
    fun `should delegate to the repository when called`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            val useCase = DeleteJournalEntryUseCase(repository)

            // Act
            val result = useCase("1")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("1", repository.lastDeletedId)
            assertEquals(1, repository.deleteEntryCallCount)
        }

    @Test
    fun `should succeed regardless of the entry's age, unlike Update`() =
        runTest {
            // Arrange - no clock/createdAt is involved at all, unlike UpdateJournalEntryUseCase
            val repository = FakeJournalRepository()
            val useCase = DeleteJournalEntryUseCase(repository)

            // Act
            val result = useCase("old-entry")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, repository.deleteEntryCallCount)
        }

    @Test
    fun `should pass a repository failure through unchanged`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            val failure = RuntimeException("delete failed")
            repository.deleteEntryResult = Result.failure(failure)
            val useCase = DeleteJournalEntryUseCase(repository)

            // Act
            val result = useCase("1")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
