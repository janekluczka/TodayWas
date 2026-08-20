package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UpdateHabitCheckInUseCaseTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")
    private val date = LocalDate.of(2026, 8, 1)

    @Test
    fun `should delegate to the repository when within the edit window`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneOffset.UTC)
            val repository = FakeHabitRepository()
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            // Act
            val result = useCase("1", date, 0, createdAt)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("1", repository.lastUpdatedHabitId)
            assertEquals(date, repository.lastUpdatedDate)
            assertEquals(0, repository.lastUpdatedValue)
        }

    @Test
    fun `should return failure and never call the repository when at the 24h boundary`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(24)), ZoneOffset.UTC)
            val repository = FakeHabitRepository()
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            // Act
            val result = useCase("1", date, 0, createdAt)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateCheckInCallCount)
        }

    @Test
    fun `should return failure and never call the repository when past the 24h boundary`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(
                createdAt.plus(Duration.ofHours(24).plusSeconds(1)),
                ZoneOffset.UTC,
            )
            val repository = FakeHabitRepository()
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            // Act
            val result = useCase("1", date, 0, createdAt)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateCheckInCallCount)
        }

    @Test
    fun `should pass a repository failure through unchanged`() =
        runTest {
            // Arrange
            val clock = Clock.fixed(createdAt, ZoneOffset.UTC)
            val repository = FakeHabitRepository()
            val failure = RuntimeException("write failed")
            repository.updateCheckInResult = Result.failure(failure)
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            // Act
            val result = useCase("1", date, 0, createdAt)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
