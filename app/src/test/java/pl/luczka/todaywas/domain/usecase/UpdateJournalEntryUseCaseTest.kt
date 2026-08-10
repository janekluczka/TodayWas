package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class UpdateJournalEntryUseCaseTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")

    @Test
    fun `should delegate to the repository with unchanged id when within the edit window`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneOffset.UTC)
            val repository = FakeJournalRepository()
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase(1L, "Edited.", createdAt)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1L, repository.lastUpdatedId)
            assertEquals("Edited.", repository.lastUpdatedText)
        }

    @Test
    fun `should return failure and never call the repository when at the 24h boundary`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(24)), ZoneOffset.UTC)
            val repository = FakeJournalRepository()
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase(1L, "Edited.", createdAt)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateEntryCallCount)
        }

    @Test
    fun `should return failure and never call the repository when past the 24h boundary`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(
                createdAt.plus(Duration.ofHours(24).plusSeconds(1)),
                ZoneOffset.UTC,
            )
            val repository = FakeJournalRepository()
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase(1L, "Edited.", createdAt)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateEntryCallCount)
        }

    @Test
    fun `should pass a repository failure through unchanged`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt, ZoneOffset.UTC)
            val repository = FakeJournalRepository()
            val failure = RuntimeException("write failed")
            repository.updateEntryResult = Result.failure(failure)
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase(1L, "Edited.", createdAt)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
