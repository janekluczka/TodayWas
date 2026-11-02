package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SaveHabitCheckInsUseCaseTest {

    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.systemDefault())
    private val date = LocalDate.of(2026, 8, 2)

    private fun checkIn(createdAt: Instant) = HabitCheckIn(
        id = "c1",
        habitId = "h1",
        date = date,
        value = 1,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `should add a check-in when the date has none stored`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val useCase = SaveHabitCheckInsUseCase(repository, clock)

            // Act
            val result = useCase("h1", mapOf(date to 1))

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(date, repository.lastLoggedDate)
            assertEquals(mapOf("h1" to 1), repository.lastLoggedValues)
            assertEquals(0, repository.updateCheckInCallCount)
        }

    @Test
    fun `should update the stored check-in when it is within the edit window`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialCheckIns = listOf(checkIn(createdAt = now.minus(Duration.ofHours(1)))),
            )
            val useCase = SaveHabitCheckInsUseCase(repository, clock)

            // Act
            val result = useCase("h1", mapOf(date to 0))

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, repository.updateCheckInCallCount)
            assertEquals(0, repository.lastUpdatedValue)
        }

    @Test
    fun `should refuse the update when the stored check-in is past the edit window`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialCheckIns = listOf(checkIn(createdAt = now.minus(Duration.ofHours(25)))),
            )
            val useCase = SaveHabitCheckInsUseCase(repository, clock)

            // Act
            val result = useCase("h1", mapOf(date to 0))

            // Assert
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateCheckInCallCount)
        }

    @Test
    fun `should pass a repository failure through unchanged when the write fails`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository(
                initialCheckIns = listOf(checkIn(createdAt = now.minus(Duration.ofHours(1)))),
            )
            val failure = RuntimeException("write failed")
            repository.updateCheckInResult = Result.failure(failure)
            val useCase = SaveHabitCheckInsUseCase(repository, clock)

            // Act
            val result = useCase("h1", mapOf(date to 0))

            // Assert
            assertEquals(failure, result.exceptionOrNull())
        }
}
