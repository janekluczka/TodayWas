package pl.luczka.todaywas.ui.onboarding.choice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.repository.OnboardingRepository
import pl.luczka.todaywas.domain.usecase.CompleteOnboardingUseCase
import pl.luczka.todaywas.ui.onboarding.AllSetReason

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingChoiceViewModelTest {

    private class FakeOnboardingRepository : OnboardingRepository {

        private val stateFlow = MutableStateFlow(
            OnboardingState(completed = false, hasSyncedLocalData = false),
        )

        var completeOnboardingResult: Result<Unit> = Result.success(Unit)
        var completeOnboardingCallCount = 0
            private set

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun completeOnboarding(): Result<Unit> {
            completeOnboardingCallCount++
            if (completeOnboardingResult.isSuccess) {
                stateFlow.value = stateFlow.value.copy(completed = true)
            }
            return completeOnboardingResult
        }

        override suspend fun markLocalDataSynced(): Result<Unit> = Result.success(Unit)

        override suspend fun resetSyncFlag(): Result<Unit> = Result.success(Unit)
    }

    private fun viewModel(repository: OnboardingRepository) =
        OnboardingChoiceViewModel(completeOnboarding = CompleteOnboardingUseCase(repository))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should complete onboarding and set Finished with NO_ACCOUNT reason when ContinueWithoutAccountClicked succeeds`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val viewModel = viewModel(repository)

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)

            // Assert
            assertEquals(1, repository.completeOnboardingCallCount)
            assertEquals(
                OnboardingChoiceNavigation.Finished(AllSetReason.NO_ACCOUNT),
                viewModel.uiState.value.pendingNavigation,
            )
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `should set saveError without navigating when ContinueWithoutAccountClicked fails`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            repository.completeOnboardingResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)

            // Assert
            assertTrue(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isSaving)
            assertNull(viewModel.uiState.value.pendingNavigation)
        }

    @Test
    fun `should succeed when ContinueWithoutAccountClicked is retried after a failed attempt`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            repository.completeOnboardingResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)
            assertTrue(viewModel.uiState.value.saveError)
            repository.completeOnboardingResult = Result.success(Unit)

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)

            // Assert
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(
                OnboardingChoiceNavigation.Finished(AllSetReason.NO_ACCOUNT),
                viewModel.uiState.value.pendingNavigation,
            )
        }

    @Test
    fun `should clear pendingNavigation when NavigationHandled`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.NavigationHandled)

            // Assert
            assertNull(viewModel.uiState.value.pendingNavigation)
        }
}
