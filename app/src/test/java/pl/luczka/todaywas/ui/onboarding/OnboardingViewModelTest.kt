package pl.luczka.todaywas.ui.onboarding

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.usecase.SelectFocusUseCase
import pl.luczka.todaywas.domain.usecase.SkipOnboardingUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeOnboardingRepository : OnboardingRepository {

        private val stateFlow = MutableStateFlow(OnboardingState(completed = false, focus = null))

        var saveFocusResult: Result<Unit> = Result.success(Unit)
        var saveFocusCallCount = 0
            private set

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun saveFocus(focus: Focus): Result<Unit> {
            saveFocusCallCount++
            if (saveFocusResult.isSuccess) {
                stateFlow.value = OnboardingState(completed = true, focus = focus)
            }
            return saveFocusResult
        }
    }

    private fun viewModel(repository: OnboardingRepository) =
        OnboardingViewModel(
            selectFocus = SelectFocusUseCase(repository),
            skipOnboarding = SkipOnboardingUseCase(repository),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is WELCOME with nothing selected`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())

            val state = viewModel.uiState.value

            assertEquals(OnboardingStep.WELCOME, state.step)
            assertNull(state.selectedFocus)
            assertNull(state.confirmedFocus)
            assertFalse(state.isSaving)
            assertFalse(state.saveError)
        }

    @Test
    fun `NextClicked from WELCOME advances to FOCUS_PICK`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())

            viewModel.onIntent(OnboardingIntent.NextClicked)

            assertEquals(OnboardingStep.FOCUS_PICK, viewModel.uiState.value.step)
        }

    @Test
    fun `NextClicked on FOCUS_PICK success advances to ACCOUNT_INFO and sets confirmedFocus`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.JOURNAL))

            viewModel.onIntent(OnboardingIntent.NextClicked)

            val state = viewModel.uiState.value
            assertEquals(Focus.JOURNAL, state.confirmedFocus)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.step)
            assertFalse(state.isSaving)
            assertFalse(state.saveError)
        }

    @Test
    fun `NextClicked on FOCUS_PICK failure sets saveError and stays on FOCUS_PICK`() =
        runTest {
            val repository = FakeOnboardingRepository()
            repository.saveFocusResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.BOTH))

            viewModel.onIntent(OnboardingIntent.NextClicked)

            val state = viewModel.uiState.value
            assertTrue(state.saveError)
            assertFalse(state.isSaving)
            assertEquals(OnboardingStep.FOCUS_PICK, state.step)
        }

    @Test
    fun `NextClicked retries after a failed attempt`() =
        runTest {
            val repository = FakeOnboardingRepository()
            repository.saveFocusResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            assertTrue(viewModel.uiState.value.saveError)
            repository.saveFocusResult = Result.success(Unit)

            viewModel.onIntent(OnboardingIntent.NextClicked)

            val state = viewModel.uiState.value
            assertFalse(state.saveError)
            assertEquals(Focus.HABIT, state.confirmedFocus)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.step)
        }

    @Test
    fun `CreateAccountClicked is a true no-op`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            val stateBefore = viewModel.uiState.value

            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)

            assertEquals(stateBefore, viewModel.uiState.value)
        }

    @Test
    fun `NextClicked on ACCOUNT_INFO advances to ALL_SET`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(OnboardingIntent.NextClicked)

            viewModel.onIntent(OnboardingIntent.NextClicked)

            assertEquals(OnboardingStep.ALL_SET, viewModel.uiState.value.step)
        }

    @Test
    fun `NextClicked on ALL_SET emits Finished`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(OnboardingIntent.NextClicked)
            runCurrent()

            assertEquals(listOf(OnboardingUiEvent.Finished), events)
            collectJob.cancel()
        }

    @Test
    fun `StepBack from FOCUS_PICK returns to WELCOME`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)

            viewModel.onIntent(OnboardingIntent.StepBack)

            assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.step)
        }

    @Test
    fun `StepBack from ACCOUNT_INFO returns to FOCUS_PICK and pre-fills confirmedFocus`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)

            viewModel.onIntent(OnboardingIntent.StepBack)

            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.FOCUS_PICK, state.step)
            assertEquals(Focus.HABIT, state.selectedFocus)
        }

    @Test
    fun `StepBack from ALL_SET returns to ACCOUNT_INFO`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.NextClicked)

            viewModel.onIntent(OnboardingIntent.StepBack)

            assertEquals(OnboardingStep.ACCOUNT_INFO, viewModel.uiState.value.step)
        }

    @Test
    fun `StepBack from WELCOME emits ExitApp without changing step`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(OnboardingIntent.StepBack)
            runCurrent()

            assertEquals(listOf(OnboardingUiEvent.ExitApp), events)
            assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.step)
            collectJob.cancel()
        }

    @Test
    fun `SkipClicked calls SkipOnboardingUseCase and emits Finished when no focus confirmed yet`() =
        runTest {
            val repository = FakeOnboardingRepository()
            val viewModel = viewModel(repository)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(OnboardingIntent.SkipClicked)
            runCurrent()

            assertEquals(1, repository.saveFocusCallCount)
            assertEquals(listOf(OnboardingUiEvent.Finished), events)
            collectJob.cancel()
        }

    @Test
    fun `SkipClicked emits Finished with no use-case call when a focus is already confirmed`() =
        runTest {
            val repository = FakeOnboardingRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(OnboardingIntent.SkipClicked)
            runCurrent()

            assertEquals(1, repository.saveFocusCallCount)
            assertEquals(Focus.JOURNAL, viewModel.uiState.value.confirmedFocus)
            assertEquals(listOf(OnboardingUiEvent.Finished), events)
            collectJob.cancel()
        }
}
