package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeOnboardingRepository

class MarkLocalDataSyncedUseCaseTest {

    @Test
    fun `should delegate to the repository and succeed`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val useCase = MarkLocalDataSyncedUseCase(repository)

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, repository.markLocalDataSyncedCallCount)
        }

    @Test
    fun `should pass a repository failure through unchanged`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val failure = RuntimeException("failed")
            repository.markLocalDataSyncedResult = Result.failure(failure)
            val useCase = MarkLocalDataSyncedUseCase(repository)

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }
}
