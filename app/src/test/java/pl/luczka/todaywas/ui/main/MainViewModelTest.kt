package pl.luczka.todaywas.ui.main

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
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.domain.usecase.SelectFocusUseCase
import pl.luczka.todaywas.domain.usecase.SkipOnboardingUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private class FakeOnboardingRepository(
        initialState: OnboardingState,
    ) : OnboardingRepository {

        private val stateFlow = MutableStateFlow(initialState)

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
        MainViewModel(
            observeOnboardingState = ObserveOnboardingStateUseCase(repository),
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
    fun `initial state is MANDATORY WELCOME when onboarding incomplete`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))

            val state = viewModel.uiState.value

            assertTrue(state.showOnboardingDialog)
            assertEquals(OnboardingMode.MANDATORY, state.onboardingMode)
            assertEquals(OnboardingStep.WELCOME, state.onboardingStep)
            assertNull(state.currentFocus)
        }

    @Test
    fun `WelcomeContinue advances to FOCUS_PICK`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))

            viewModel.onIntent(MainIntent.WelcomeContinue)

            assertEquals(OnboardingStep.FOCUS_PICK, viewModel.uiState.value.onboardingStep)
        }

    @Test
    fun `ConfirmSelection success in MANDATORY mode advances without closing dialog`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = false, focus = null))
            val viewModel = viewModel(repository)
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.JOURNAL))

            viewModel.onIntent(MainIntent.ConfirmSelection)

            val state = viewModel.uiState.value
            assertEquals(Focus.JOURNAL, state.currentFocus)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.onboardingStep)
            assertTrue(state.showOnboardingDialog)
            assertFalse(state.isSaving)
            assertFalse(state.saveError)
        }

    @Test
    fun `ConfirmSelection success in REPICK mode closes dialog directly`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = true, focus = Focus.HABIT))
            val viewModel = viewModel(repository)
            viewModel.onIntent(MainIntent.ChangeFocusRequested)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.JOURNAL))

            viewModel.onIntent(MainIntent.ConfirmSelection)

            val state = viewModel.uiState.value
            assertEquals(Focus.JOURNAL, state.currentFocus)
            assertFalse(state.showOnboardingDialog)
        }

    @Test
    fun `ConfirmSelection failure sets saveError and stays on FOCUS_PICK`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = false, focus = null))
            repository.saveFocusResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.BOTH))

            viewModel.onIntent(MainIntent.ConfirmSelection)

            val state = viewModel.uiState.value
            assertTrue(state.saveError)
            assertFalse(state.isSaving)
            assertEquals(OnboardingStep.FOCUS_PICK, state.onboardingStep)
        }

    @Test
    fun `CreateAccountClicked is a true no-op`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(MainIntent.ConfirmSelection)
            val stateBefore = viewModel.uiState.value

            viewModel.onIntent(MainIntent.CreateAccountClicked)

            assertEquals(stateBefore, viewModel.uiState.value)
        }

    @Test
    fun `AccountContinue advances ACCOUNT_INFO to ALL_SET`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(MainIntent.ConfirmSelection)

            viewModel.onIntent(MainIntent.AccountContinue)

            assertEquals(OnboardingStep.ALL_SET, viewModel.uiState.value.onboardingStep)
        }

    @Test
    fun `FinishOnboarding closes the dialog`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(MainIntent.ConfirmSelection)
            viewModel.onIntent(MainIntent.AccountContinue)

            viewModel.onIntent(MainIntent.FinishOnboarding)

            assertFalse(viewModel.uiState.value.showOnboardingDialog)
        }

    @Test
    fun `StepBack from FOCUS_PICK returns to WELCOME`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))
            viewModel.onIntent(MainIntent.WelcomeContinue)

            viewModel.onIntent(MainIntent.StepBack)

            assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.onboardingStep)
        }

    @Test
    fun `StepBack from ACCOUNT_INFO returns to FOCUS_PICK and pre-fills currentFocus`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.HABIT))
            viewModel.onIntent(MainIntent.ConfirmSelection)

            viewModel.onIntent(MainIntent.StepBack)

            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.FOCUS_PICK, state.onboardingStep)
            assertEquals(Focus.HABIT, state.selectedFocusInDialog)
        }

    @Test
    fun `StepBack from ALL_SET returns to ACCOUNT_INFO`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.HABIT))
            viewModel.onIntent(MainIntent.ConfirmSelection)
            viewModel.onIntent(MainIntent.AccountContinue)

            viewModel.onIntent(MainIntent.StepBack)

            assertEquals(OnboardingStep.ACCOUNT_INFO, viewModel.uiState.value.onboardingStep)
        }

    @Test
    fun `StepBack from WELCOME emits ExitApp event without changing step`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository(OnboardingState(completed = false, focus = null)))
            val events = mutableListOf<MainUiEvent>()
            val collectJob =
                launch {
                    viewModel.events.collect { events.add(it) }
                }

            viewModel.onIntent(MainIntent.StepBack)
            runCurrent()

            assertEquals(listOf(MainUiEvent.ExitApp), events)
            assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.onboardingStep)
            collectJob.cancel()
        }

    @Test
    fun `SkipOnboarding calls SkipOnboardingUseCase and closes when no focus is confirmed yet`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = false, focus = null))
            val viewModel = viewModel(repository)

            viewModel.onIntent(MainIntent.SkipOnboarding)

            assertEquals(1, repository.saveFocusCallCount)
            assertFalse(viewModel.uiState.value.showOnboardingDialog)
        }

    @Test
    fun `SkipOnboarding closes with no use-case call when a focus is already confirmed`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = false, focus = null))
            val viewModel = viewModel(repository)
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.JOURNAL))
            viewModel.onIntent(MainIntent.ConfirmSelection)

            viewModel.onIntent(MainIntent.SkipOnboarding)

            assertEquals(1, repository.saveFocusCallCount)
            assertEquals(Focus.JOURNAL, viewModel.uiState.value.currentFocus)
            assertFalse(viewModel.uiState.value.showOnboardingDialog)
        }

    @Test
    fun `ChangeFocusRequested opens REPICK mode pre-filled to currentFocus`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = true, focus = Focus.HABIT))
            val viewModel = viewModel(repository)

            viewModel.onIntent(MainIntent.ChangeFocusRequested)

            val state = viewModel.uiState.value
            assertTrue(state.showOnboardingDialog)
            assertEquals(OnboardingMode.REPICK, state.onboardingMode)
            assertEquals(OnboardingStep.FOCUS_PICK, state.onboardingStep)
            assertEquals(Focus.HABIT, state.selectedFocusInDialog)
        }

    @Test
    fun `RetrySave re-invokes the last selection attempt`() =
        runTest {
            val repository = FakeOnboardingRepository(OnboardingState(completed = false, focus = null))
            repository.saveFocusResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(MainIntent.WelcomeContinue)
            viewModel.onIntent(MainIntent.FocusOptionSelected(Focus.HABIT))
            viewModel.onIntent(MainIntent.ConfirmSelection)
            assertTrue(viewModel.uiState.value.saveError)
            repository.saveFocusResult = Result.success(Unit)

            viewModel.onIntent(MainIntent.RetrySave)

            val state = viewModel.uiState.value
            assertFalse(state.saveError)
            assertEquals(Focus.HABIT, state.currentFocus)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.onboardingStep)
        }
}
