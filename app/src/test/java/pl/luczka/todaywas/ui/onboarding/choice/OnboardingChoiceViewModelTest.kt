package pl.luczka.todaywas.ui.onboarding.choice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `should complete onboarding and emit Finished with NO_ACCOUNT reason when ContinueWithoutAccountClicked succeeds`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val viewModel = viewModel(repository)
            val events = mutableListOf<OnboardingChoiceUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)
            runCurrent()

            // Assert
            assertEquals(1, repository.completeOnboardingCallCount)
            assertEquals(
                listOf(OnboardingChoiceUiEvent.Finished(AllSetReason.NO_ACCOUNT)),
                events,
            )
            assertFalse(viewModel.uiState.value.isSaving)
            collectJob.cancel()
        }

    @Test
    fun `should set saveError without emitting Finished when ContinueWithoutAccountClicked fails`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            repository.completeOnboardingResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            val events = mutableListOf<OnboardingChoiceUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)
            runCurrent()

            // Assert
            assertTrue(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(emptyList<OnboardingChoiceUiEvent>(), events)
            collectJob.cancel()
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
            val events = mutableListOf<OnboardingChoiceUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.ContinueWithoutAccountClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(
                listOf(OnboardingChoiceUiEvent.Finished(AllSetReason.NO_ACCOUNT)),
                events,
            )
            collectJob.cancel()
        }

    @Test
    fun `should emit NavigateToAccountSetup when SignInSignUpClicked`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            val events = mutableListOf<OnboardingChoiceUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingChoiceIntent.SignInSignUpClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(OnboardingChoiceUiEvent.NavigateToAccountSetup), events)
            collectJob.cancel()
        }
}
