package pl.luczka.todaywas.core.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.core.testing.repository.FakeHabitRepository

class DeleteHabitUseCaseTest {

    @Test
    fun `should delegate to the repository when called`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val useCase = DeleteHabitUseCase(repository)

            // Act
            val result = useCase("1")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("1", repository.lastDeletedHabitId)
            assertEquals(1, repository.deleteHabitCallCount)
        }

    @Test
    fun `should pass the failure through unchanged when the repository fails`() =
        runTest {
            // Arrange
            val repository = FakeHabitRepository()
            val failure = RuntimeException("delete failed")
            repository.deleteHabitResult = Result.failure(failure)
            val useCase = DeleteHabitUseCase(repository)

            // Act
            val result = useCase("1")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
