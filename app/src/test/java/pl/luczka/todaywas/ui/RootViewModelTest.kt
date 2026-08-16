package pl.luczka.todaywas.ui

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
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.repository.OnboardingRepository
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private class FakeOnboardingRepository(
        initialState: OnboardingState,
    ) : OnboardingRepository {

        val stateFlow = MutableStateFlow(initialState)

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun completeOnboarding(): Result<Unit> = Result.success(Unit)

        override suspend fun markLocalDataSynced(): Result<Unit> = Result.success(Unit)

        override suspend fun resetSyncFlag(): Result<Unit> = Result.success(Unit)
    }

    private fun viewModel(repository: OnboardingRepository) =
        RootViewModel(observeOnboardingState = ObserveOnboardingStateUseCase(repository))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should resolve to OnboardingKey when initial state is incomplete`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository(
                OnboardingState(completed = false, hasSyncedLocalData = false),
            )

            // Act
            val viewModel = viewModel(repository)
            val destination = viewModel.initialDestination.value

            // Assert
            assertEquals(OnboardingKey, destination)
        }

    @Test
    fun `should resolve to MainKey when initial state is already completed`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository(
                OnboardingState(completed = true, hasSyncedLocalData = false),
            )

            // Act
            val viewModel = viewModel(repository)
            val destination = viewModel.initialDestination.value

            // Assert
            assertEquals(MainKey, destination)
        }

    @Test
    fun `should not change the resolved destination when a later emission flips completed to true`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository(
                OnboardingState(completed = false, hasSyncedLocalData = false),
            )
            val viewModel = viewModel(repository)
            assertEquals(OnboardingKey, viewModel.initialDestination.value)

            // Act
            // Mirrors reaching ALL_SET (or Skip) mid-flow: Room flips `completed` before Main shows.
            repository.stateFlow.value = OnboardingState(completed = true, hasSyncedLocalData = false)

            // Assert
            assertEquals(OnboardingKey, viewModel.initialDestination.value)
        }
}
