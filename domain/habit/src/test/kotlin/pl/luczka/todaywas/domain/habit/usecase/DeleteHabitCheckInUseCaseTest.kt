package pl.luczka.todaywas.domain.habit.usecase

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.core.testing.repository.FakeHabitRepository

class DeleteHabitCheckInUseCaseTest {

    @Test
    fun `should delegate to the repository when called`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val useCase = DeleteHabitCheckInUseCase(repository)

            // Act
            val result = useCase("1", LocalDate.of(2026, 7, 27))

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("1", repository.lastDeletedCheckInHabitId)
            assertEquals(LocalDate.of(2026, 7, 27), repository.lastDeletedCheckInDate)
            assertEquals(1, repository.deleteCheckInCallCount)
        }

    @Test
    fun `should pass the failure through unchanged when the repository fails`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val failure = RuntimeException("delete failed")
            repository.deleteCheckInResult = Result.failure(failure)
            val useCase = DeleteHabitCheckInUseCase(repository)

            // Act
            val result = useCase("1", LocalDate.of(2026, 7, 27))

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
