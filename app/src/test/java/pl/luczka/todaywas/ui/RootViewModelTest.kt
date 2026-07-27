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
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private class FakeOnboardingRepository(
        initialState: OnboardingState,
    ) : OnboardingRepository {

        val stateFlow = MutableStateFlow(initialState)

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun saveFocus(focus: Focus): Result<Unit> = Result.success(Unit)
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
    fun `resolves to OnboardingKey when initial state is incomplete`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))

            assertEquals(OnboardingKey, viewModel.initialDestination.value)
        }

    @Test
    fun `resolves to MainKey when initial state is already completed`() =
        runTest {
            val viewModel =
                viewModel(FakeOnboardingRepository(OnboardingState(completed = true, focus = Focus.BOTH)))

            assertEquals(MainKey, viewModel.initialDestination.value)
        }

    @Test
    fun `a later emission flipping completed to true does not change the resolved destination`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = false, focus = null))
            val viewModel = viewModel(repository)
            assertEquals(OnboardingKey, viewModel.initialDestination.value)

            // Mirrors selecting a focus mid-flow: Room flips `completed` before Account/All-set show.
            repository.stateFlow.value = OnboardingState(completed = true, focus = Focus.JOURNAL)

            assertEquals(OnboardingKey, viewModel.initialDestination.value)
        }
}
