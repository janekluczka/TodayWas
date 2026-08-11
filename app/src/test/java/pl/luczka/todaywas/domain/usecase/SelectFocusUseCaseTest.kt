package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState

class SelectFocusUseCaseTest {

    private class FakeRepository : OnboardingRepository {

        var lastSavedFocus: Focus? = null
            private set

        override fun observeState(): Flow<OnboardingState> =
            flowOf(
                OnboardingState(
                    completed = false,
                    focus = null,
                    hasSyncedLocalData = false,
                ),
            )

        override suspend fun saveFocus(focus: Focus): Result<Unit> {
            lastSavedFocus = focus
            return Result.success(Unit)
        }

        override suspend fun markLocalDataSynced(): Result<Unit> = Result.success(Unit)

        override suspend fun resetSyncFlag(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `should pass the given focus through unchanged when invoked`() =
        runTest {
            for (focus in Focus.entries) {
                // Arrange
                val repository = FakeRepository()
                val useCase = SelectFocusUseCase(repository)

                // Act
                useCase(focus)

                // Assert
                assertEquals(focus, repository.lastSavedFocus)
            }
        }
}
