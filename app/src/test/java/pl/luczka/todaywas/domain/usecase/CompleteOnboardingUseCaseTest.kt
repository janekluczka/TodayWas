package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.repository.OnboardingRepository

class CompleteOnboardingUseCaseTest {

    private class FakeRepository(
        private val priorState: OnboardingState,
    ) : OnboardingRepository {

        var completeOnboardingCallCount = 0
            private set

        override fun observeState(): Flow<OnboardingState> = flowOf(priorState)

        override suspend fun completeOnboarding(): Result<Unit> {
            completeOnboardingCallCount++
            return Result.success(Unit)
        }

        override suspend fun markLocalDataSynced(): Result<Unit> = Result.success(Unit)

        override suspend fun resetSyncFlag(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `should mark onboarding completed regardless of prior state when invoked`() =
        runTest {
            val priorStates = listOf(
                OnboardingState(completed = false, hasSyncedLocalData = false),
                OnboardingState(completed = true, hasSyncedLocalData = false),
            )

            for (priorState in priorStates) {
                // Arrange
                val repository = FakeRepository(priorState)
                val useCase = CompleteOnboardingUseCase(repository)

                // Act
                useCase()

                // Assert
                assertEquals(1, repository.completeOnboardingCallCount)
            }
        }
}
