package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.data.repository.FakeOnboardingRepository

class ClearSyncedLocalDataUseCaseTest {

    @Test
    fun `should clear both repositories and reset the sync flag when all succeed`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val useCase = ClearSyncedLocalDataUseCase(journalRepository, habitRepository, onboardingRepository)

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, journalRepository.clearLocalCallCount)
            assertEquals(1, habitRepository.clearLocalCallCount)
            assertEquals(1, onboardingRepository.resetSyncFlagCallCount)
        }

    @Test
    fun `should return failure when the journal repository clear fails`() =
        runTest {
            // Arrange
            val journalRepository = FakeJournalRepository()
            journalRepository.clearLocalResult = Result.failure(RuntimeException("failed"))
            val habitRepository = FakeHabitRepository()
            val onboardingRepository = FakeOnboardingRepository()
            val useCase = ClearSyncedLocalDataUseCase(journalRepository, habitRepository, onboardingRepository)

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isFailure)
        }
}
