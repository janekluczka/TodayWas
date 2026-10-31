package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.model.JournalEntryNotFoundException
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UpdateJournalEntryUseCaseTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")

    private val storedEntry = JournalEntry(
        id = "1",
        date = LocalDate.of(2026, 8, 1),
        text = "Original.",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `should delegate to the repository with unchanged id when within the edit window`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneOffset.UTC)
            val repository = FakeJournalRepository(listOf(storedEntry))
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase("1", "Edited.")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("1", repository.lastUpdatedId)
            assertEquals("Edited.", repository.lastUpdatedText)
        }

    @Test
    fun `should return failure and never call the repository when at the 24h boundary`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(24)), ZoneOffset.UTC)
            val repository = FakeJournalRepository(listOf(storedEntry))
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase("1", "Edited.")

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
            val repository = FakeJournalRepository(listOf(storedEntry))
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase("1", "Edited.")

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateEntryCallCount)
        }

    @Test
    fun `should return failure and never call the repository when the entry does not exist`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt, ZoneOffset.UTC)
            val repository = FakeJournalRepository()
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase("missing", "Edited.")

            // Assert
            assertTrue(result.exceptionOrNull() is JournalEntryNotFoundException)
            assertEquals(0, repository.updateEntryCallCount)
        }

    @Test
    fun `should pass a repository failure through unchanged`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt, ZoneOffset.UTC)
            val repository = FakeJournalRepository(listOf(storedEntry))
            val failure = RuntimeException("write failed")
            repository.updateEntryResult = Result.failure(failure)
            val useCase = UpdateJournalEntryUseCase(repository, clock)

            // Act
            val result = useCase("1", "Edited.")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
