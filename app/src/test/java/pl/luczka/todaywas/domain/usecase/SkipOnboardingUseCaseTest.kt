package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState

class SkipOnboardingUseCaseTest {

    private class FakeRepository(
        private val priorState: OnboardingState,
    ) : OnboardingRepository {

        var lastSavedFocus: Focus? = null
            private set

        override fun observeState(): Flow<OnboardingState> = flowOf(priorState)

        override suspend fun saveFocus(focus: Focus): Result<Unit> {
            lastSavedFocus = focus
            return Result.success(Unit)
        }
    }

    @Test
    fun `invoke saves Both regardless of prior state`() =
        runTest {
            val priorStates = listOf(
                OnboardingState(
                    completed = false,
                    focus = null,
                ),
                OnboardingState(
                    completed = true,
                    focus = Focus.JOURNAL,
                ),
                OnboardingState(
                    completed = true,
                    focus = Focus.HABIT,
                ),
            )

            for (priorState in priorStates) {
                val repository = FakeRepository(priorState)
                val useCase = SkipOnboardingUseCase(repository)

                useCase()

                assertEquals(Focus.BOTH, repository.lastSavedFocus)
            }
        }
}
