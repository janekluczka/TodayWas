package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.data.repository.FakeJournalRepository

class SyncLocalDataUseCaseTest {

    @Test
    fun `should succeed when both repositories sync successfully`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val useCase = SyncLocalDataUseCase(journalRepository, habitRepository)

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, journalRepository.syncWithRemoteCallCount)
            assertEquals(1, habitRepository.syncWithRemoteCallCount)
        }

    @Test
    fun `should return failure when the journal repository sync fails`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            journalRepository.syncWithRemoteResult = Result.failure(RuntimeException("failed"))
            val habitRepository = FakeHabitRepository()
            val useCase = SyncLocalDataUseCase(journalRepository, habitRepository)

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isFailure)
        }

    @Test
    fun `should return failure when the habit repository sync fails`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            habitRepository.syncWithRemoteResult = Result.failure(RuntimeException("failed"))
            val useCase = SyncLocalDataUseCase(journalRepository, habitRepository)

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isFailure)
        }
}
