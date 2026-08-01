package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UpdateHabitCheckInUseCaseTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")
    private val date = LocalDate.of(2026, 8, 1)

    @Test
    fun `within window delegates to the repository`() =
        runTest {
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneOffset.UTC)
            val repository = FakeHabitRepository()
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            val result = useCase(1L, date, 0, createdAt)

            assertTrue(result.isSuccess)
            assertEquals(1L, repository.lastUpdatedHabitId)
            assertEquals(date, repository.lastUpdatedDate)
            assertEquals(0, repository.lastUpdatedValue)
        }

    @Test
    fun `at the 24h boundary returns failure and never calls the repository`() =
        runTest {
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(24)), ZoneOffset.UTC)
            val repository = FakeHabitRepository()
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            val result = useCase(1L, date, 0, createdAt)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateCheckInCallCount)
        }

    @Test
    fun `past the 24h boundary returns failure and never calls the repository`() =
        runTest {
            val clock = Clock.fixed(createdAt.plus(Duration.ofHours(24).plusSeconds(1)), ZoneOffset.UTC)
            val repository = FakeHabitRepository()
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            val result = useCase(1L, date, 0, createdAt)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EditWindowExpiredException)
            assertEquals(0, repository.updateCheckInCallCount)
        }

    @Test
    fun `a repository failure passes through unchanged`() =
        runTest {
            val clock = Clock.fixed(createdAt, ZoneOffset.UTC)
            val repository = FakeHabitRepository()
            val failure = RuntimeException("write failed")
            repository.updateCheckInResult = Result.failure(failure)
            val useCase = UpdateHabitCheckInUseCase(repository, clock)

            val result = useCase(1L, date, 0, createdAt)

            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
