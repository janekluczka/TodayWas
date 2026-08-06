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
import pl.luczka.todaywas.data.repository.FakeAuthRepository
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.SelectFocusUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SkipOnboardingUseCase
import pl.luczka.todaywas.ui.auth.AuthFormMode
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FocusUiState

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeOnboardingRepository : OnboardingRepository {

        private val stateFlow = MutableStateFlow(
            OnboardingState(
                completed = false,
                focus = null,
            ),
        )

        var saveFocusResult: Result<Unit> = Result.success(Unit)
        var saveFocusCallCount = 0
            private set

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun saveFocus(focus: Focus): Result<Unit> {
            saveFocusCallCount++
            if (saveFocusResult.isSuccess) {
                stateFlow.value = OnboardingState(
                    completed = true,
                    focus = focus,
                )
            }
            return saveFocusResult
        }
    }

    private fun viewModel(
        repository: OnboardingRepository,
        authRepository: FakeAuthRepository = FakeAuthRepository(),
    ) = OnboardingViewModel(
        selectFocus = SelectFocusUseCase(repository),
        skipOnboarding = SkipOnboardingUseCase(repository),
        observeAuthState = ObserveAuthStateUseCase(authRepository),
        signUpWithEmail = SignUpWithEmailUseCase(authRepository),
        signInWithEmail = SignInWithEmailUseCase(authRepository),
        signInWithGoogle = SignInWithGoogleUseCase(authRepository),
    )

    private fun advanceToAccountInfo(viewModel: OnboardingViewModel) {
        viewModel.onIntent(OnboardingIntent.NextClicked)
        viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))
        viewModel.onIntent(OnboardingIntent.NextClicked)
    }

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
            assertEquals(AccountSubStep.CHOICE, state.accountSubStep)
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
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))

            viewModel.onIntent(OnboardingIntent.NextClicked)

            val state = viewModel.uiState.value
            assertEquals(FocusUiState.JOURNAL, state.confirmedFocus)
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
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.BOTH))

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
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            assertTrue(viewModel.uiState.value.saveError)
            repository.saveFocusResult = Result.success(Unit)

            viewModel.onIntent(OnboardingIntent.NextClicked)

            val state = viewModel.uiState.value
            assertFalse(state.saveError)
            assertEquals(FocusUiState.HABIT, state.confirmedFocus)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.step)
        }

    @Test
    fun `CreateAccountClicked moves to FORM in SIGN_UP mode`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)

            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)

            val state = viewModel.uiState.value
            assertEquals(AccountSubStep.FORM, state.accountSubStep)
            assertEquals(AuthFormMode.SIGN_UP, state.authForm.mode)
        }

    @Test
    fun `SignInClicked moves to FORM in SIGN_IN mode`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)

            viewModel.onIntent(OnboardingIntent.SignInClicked)

            val state = viewModel.uiState.value
            assertEquals(AccountSubStep.FORM, state.accountSubStep)
            assertEquals(AuthFormMode.SIGN_IN, state.authForm.mode)
        }

    @Test
    fun `BackToChoiceClicked returns to CHOICE`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)
            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)

            viewModel.onIntent(OnboardingIntent.BackToChoiceClicked)

            assertEquals(AccountSubStep.CHOICE, viewModel.uiState.value.accountSubStep)
        }

    @Test
    fun `submit in SIGN_UP mode with valid fields calls signUpWithEmail and resets the form on success`() =
        runTest {
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToAccountInfo(viewModel)
            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)
            viewModel.onIntent(OnboardingIntent.FirstNameChanged("Jane"))
            viewModel.onIntent(OnboardingIntent.LastNameChanged("Doe"))
            viewModel.onIntent(OnboardingIntent.EmailChanged("person@example.com"))
            viewModel.onIntent(OnboardingIntent.PasswordChanged("password123"))

            viewModel.onIntent(OnboardingIntent.SubmitClicked)

            assertEquals(1, authRepository.signUpCallCount)
            assertEquals("Jane", authRepository.lastSignUpFirstName)
            assertEquals("Doe", authRepository.lastSignUpLastName)
            assertFalse(viewModel.uiState.value.authForm.isSubmitting)
        }

    @Test
    fun `submit in SIGN_UP mode without a name sets name errors without calling the repository`() =
        runTest {
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToAccountInfo(viewModel)
            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)
            viewModel.onIntent(OnboardingIntent.EmailChanged("person@example.com"))
            viewModel.onIntent(OnboardingIntent.PasswordChanged("password123"))

            viewModel.onIntent(OnboardingIntent.SubmitClicked)

            val form = viewModel.uiState.value.authForm
            assertTrue(form.firstNameError)
            assertTrue(form.lastNameError)
            assertEquals(0, authRepository.signUpCallCount)
        }

    @Test
    fun `submit failure emits a ShowError event and stops submitting`() =
        runTest {
            val authRepository = FakeAuthRepository()
            authRepository.signUpError = AuthError.EmailAlreadyRegistered
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToAccountInfo(viewModel)
            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)
            viewModel.onIntent(OnboardingIntent.FirstNameChanged("Jane"))
            viewModel.onIntent(OnboardingIntent.LastNameChanged("Doe"))
            viewModel.onIntent(OnboardingIntent.EmailChanged("person@example.com"))
            viewModel.onIntent(OnboardingIntent.PasswordChanged("password123"))
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(OnboardingIntent.SubmitClicked)
            runCurrent()

            assertEquals(
                listOf(OnboardingUiEvent.ShowError(AuthErrorUiState.EMAIL_ALREADY_REGISTERED)),
                events,
            )
            assertFalse(viewModel.uiState.value.authForm.isSubmitting)
            collectJob.cancel()
        }

    @Test
    fun `GoogleSignInFailed emits a ShowError event with Unknown`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(OnboardingIntent.GoogleSignInFailed)
            runCurrent()

            assertEquals(listOf(OnboardingUiEvent.ShowError(AuthErrorUiState.UNKNOWN)), events)
            collectJob.cancel()
        }

    @Test
    fun `NextClicked on ACCOUNT_INFO advances to ALL_SET when nothing was chosen`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)

            viewModel.onIntent(OnboardingIntent.NextClicked)

            assertEquals(OnboardingStep.ALL_SET, viewModel.uiState.value.step)
        }

    @Test
    fun `NextClicked on ACCOUNT_INFO advances to ALL_SET while mid-form`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)
            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)
            viewModel.onIntent(OnboardingIntent.EmailChanged("person@example.com"))

            viewModel.onIntent(OnboardingIntent.NextClicked)

            assertEquals(OnboardingStep.ALL_SET, viewModel.uiState.value.step)
        }

    @Test
    fun `NextClicked on ACCOUNT_INFO advances to ALL_SET after a successful sign-up`() =
        runTest {
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToAccountInfo(viewModel)
            viewModel.onIntent(OnboardingIntent.CreateAccountClicked)
            viewModel.onIntent(OnboardingIntent.FirstNameChanged("Jane"))
            viewModel.onIntent(OnboardingIntent.LastNameChanged("Doe"))
            viewModel.onIntent(OnboardingIntent.EmailChanged("person@example.com"))
            viewModel.onIntent(OnboardingIntent.PasswordChanged("password123"))
            viewModel.onIntent(OnboardingIntent.SubmitClicked)

            viewModel.onIntent(OnboardingIntent.NextClicked)

            assertEquals(OnboardingStep.ALL_SET, viewModel.uiState.value.step)
        }

    @Test
    fun `authState reflects a session that becomes signed in`() =
        runTest {
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedOut)
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)

            authRepository.emit(AuthState.SignedIn(userId = "u1", email = "person@example.com"))

            assertEquals(
                AuthStateUi.SignedIn(email = "person@example.com"),
                viewModel.uiState.value.authState,
            )
        }

    @Test
    fun `NextClicked on ALL_SET emits Finished`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))
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
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)

            viewModel.onIntent(OnboardingIntent.StepBack)

            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.FOCUS_PICK, state.step)
            assertEquals(FocusUiState.HABIT, state.selectedFocus)
        }

    @Test
    fun `StepBack from ALL_SET returns to ACCOUNT_INFO`() =
        runTest {
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.HABIT))
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
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(OnboardingIntent.SkipClicked)
            runCurrent()

            assertEquals(1, repository.saveFocusCallCount)
            assertEquals(FocusUiState.JOURNAL, viewModel.uiState.value.confirmedFocus)
            assertEquals(listOf(OnboardingUiEvent.Finished), events)
            collectJob.cancel()
        }
}
