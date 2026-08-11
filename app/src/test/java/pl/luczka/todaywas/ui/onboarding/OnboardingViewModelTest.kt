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
import pl.luczka.todaywas.data.repository.FakeHabitRepository
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.usecase.GetLocalDataSummaryUseCase
import pl.luczka.todaywas.domain.usecase.MarkLocalDataSyncedUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.domain.usecase.SelectFocusUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SkipOnboardingUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FocusUiState
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeOnboardingRepository : OnboardingRepository {

        private val stateFlow = MutableStateFlow(
            OnboardingState(
                completed = false,
                focus = null,
                hasSyncedLocalData = false,
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
                    hasSyncedLocalData = false,
                )
            }
            return saveFocusResult
        }

        var markLocalDataSyncedCallCount = 0
            private set

        override suspend fun markLocalDataSynced(): Result<Unit> {
            markLocalDataSyncedCallCount++
            return Result.success(Unit)
        }

        override suspend fun resetSyncFlag(): Result<Unit> = Result.success(Unit)
    }

    private fun viewModel(
        repository: OnboardingRepository,
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        journalRepository: FakeJournalRepository = FakeJournalRepository(),
        habitRepository: FakeHabitRepository = FakeHabitRepository(),
    ) = OnboardingViewModel(
        selectFocus = SelectFocusUseCase(repository),
        skipOnboarding = SkipOnboardingUseCase(repository),
        observeAuthState = ObserveAuthStateUseCase(authRepository),
        observeOnboardingState = ObserveOnboardingStateUseCase(repository),
        signUpWithEmail = SignUpWithEmailUseCase(authRepository),
        signInWithEmail = SignInWithEmailUseCase(authRepository),
        signInWithGoogle = SignInWithGoogleUseCase(authRepository),
        getLocalDataSummary = GetLocalDataSummaryUseCase(journalRepository, habitRepository),
        syncLocalData = SyncLocalDataUseCase(journalRepository, habitRepository),
        markLocalDataSynced = MarkLocalDataSyncedUseCase(repository),
    )

    private fun advanceToAccountInfo(viewModel: OnboardingViewModel) {
        viewModel.onIntent(OnboardingIntent.NextClicked)
        viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))
        viewModel.onIntent(OnboardingIntent.NextClicked)
    }

    private fun advanceToSignIn(viewModel: OnboardingViewModel) {
        advanceToAccountInfo(viewModel)
        viewModel.onIntent(OnboardingIntent.SignInSignUpClicked)
    }

    private fun advanceToSignUp(viewModel: OnboardingViewModel) {
        advanceToSignIn(viewModel)
        viewModel.onIntent(OnboardingIntent.SignUpLinkClicked)
    }

    private fun fillSignUpForm(viewModel: OnboardingViewModel) {
        viewModel.onIntent(OnboardingIntent.SignUpEmailChanged("person@example.com"))
        viewModel.onIntent(OnboardingIntent.SignUpPasswordChanged("password123"))
        viewModel.onIntent(OnboardingIntent.SignUpRepeatPasswordChanged("password123"))
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
    fun `should be WELCOME with nothing selected on initial state`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())

            // Act
            val state = viewModel.uiState.value

            // Assert
            assertEquals(OnboardingStep.WELCOME, state.step)
            assertNull(state.selectedFocus)
            assertNull(state.confirmedFocus)
            assertFalse(state.isSaving)
            assertFalse(state.saveError)
            assertEquals(AccountSubStep.CHOICE, state.accountSubStep)
        }

    @Test
    fun `should advance to FOCUS_PICK when NextClicked from WELCOME`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())

            // Act
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Assert
            assertEquals(OnboardingStep.FOCUS_PICK, viewModel.uiState.value.step)
        }

    @Test
    fun `should advance to ACCOUNT_INFO and set confirmedFocus when NextClicked on FOCUS_PICK succeeds`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))

            // Act
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Assert
            val state = viewModel.uiState.value
            assertEquals(FocusUiState.JOURNAL, state.confirmedFocus)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.step)
            assertFalse(state.isSaving)
            assertFalse(state.saveError)
        }

    @Test
    fun `should set saveError and stay on FOCUS_PICK when NextClicked on FOCUS_PICK fails`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            repository.saveFocusResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.BOTH))

            // Act
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Assert
            val state = viewModel.uiState.value
            assertTrue(state.saveError)
            assertFalse(state.isSaving)
            assertEquals(OnboardingStep.FOCUS_PICK, state.step)
        }

    @Test
    fun `should succeed when NextClicked is retried after a failed attempt`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            repository.saveFocusResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            assertTrue(viewModel.uiState.value.saveError)
            repository.saveFocusResult = Result.success(Unit)

            // Act
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Assert
            val state = viewModel.uiState.value
            assertFalse(state.saveError)
            assertEquals(FocusUiState.HABIT, state.confirmedFocus)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.step)
        }

    @Test
    fun `should move to SIGN_IN when SignInSignUpClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.SignInSignUpClicked)

            // Assert
            assertEquals(AccountSubStep.SIGN_IN, viewModel.uiState.value.accountSubStep)
        }

    @Test
    fun `should move to SIGN_UP when SignUpLinkClicked from SIGN_IN`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToSignIn(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.SignUpLinkClicked)

            // Assert
            assertEquals(AccountSubStep.SIGN_UP, viewModel.uiState.value.accountSubStep)
        }

    @Test
    fun `should return to CHOICE when StepBack from SIGN_IN`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToSignIn(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.StepBack)

            // Assert
            assertEquals(AccountSubStep.CHOICE, viewModel.uiState.value.accountSubStep)
            assertEquals(OnboardingStep.ACCOUNT_INFO, viewModel.uiState.value.step)
        }

    @Test
    fun `should return to SIGN_IN when StepBack from SIGN_UP`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToSignUp(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.StepBack)

            // Assert
            assertEquals(AccountSubStep.SIGN_IN, viewModel.uiState.value.accountSubStep)
            assertEquals(OnboardingStep.ACCOUNT_INFO, viewModel.uiState.value.step)
        }

    @Test
    fun `should jump straight to ALL_SET with NO_ACCOUNT reason when ContinueWithoutAccountClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.ContinueWithoutAccountClicked)

            // Assert
            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.ALL_SET, state.step)
            assertEquals(AllSetReason.NO_ACCOUNT, state.allSetReason)
        }

    @Test
    fun `should call signInWithEmail and jump to ALL_SET with SIGNED_IN reason when sign-in submit has valid fields`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToSignIn(viewModel)
            viewModel.onIntent(OnboardingIntent.SignInEmailChanged("person@example.com"))
            viewModel.onIntent(OnboardingIntent.SignInPasswordChanged("password123"))

            // Act
            viewModel.onIntent(OnboardingIntent.SignInSubmitClicked)

            // Assert
            assertEquals(1, authRepository.signInCallCount)
            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.ALL_SET, state.step)
            assertEquals(AllSetReason.SIGNED_IN, state.allSetReason)
            assertFalse(state.signInForm.isSubmitting)
        }

    @Test
    fun `should set emailError without calling the repository when sign-in submit has an invalid email`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToSignIn(viewModel)
            viewModel.onIntent(OnboardingIntent.SignInEmailChanged("not-an-email"))
            viewModel.onIntent(OnboardingIntent.SignInPasswordChanged("password123"))

            // Act
            viewModel.onIntent(OnboardingIntent.SignInSubmitClicked)

            // Assert
            assertTrue(viewModel.uiState.value.signInForm.emailError)
            assertEquals(0, authRepository.signInCallCount)
            assertEquals(OnboardingStep.ACCOUNT_INFO, viewModel.uiState.value.step)
        }

    @Test
    fun `should emit a ShowError event and stop submitting without leaving ACCOUNT_INFO when sign-in submit fails`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            authRepository.signInError = AuthError.InvalidCredentials
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToSignIn(viewModel)
            viewModel.onIntent(OnboardingIntent.SignInEmailChanged("person@example.com"))
            viewModel.onIntent(OnboardingIntent.SignInPasswordChanged("password123"))
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingIntent.SignInSubmitClicked)
            runCurrent()

            // Assert
            assertEquals(
                listOf(OnboardingUiEvent.ShowError(AuthErrorUiState.INVALID_CREDENTIALS)),
                events,
            )
            val state = viewModel.uiState.value
            assertFalse(state.signInForm.isSubmitting)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.step)
            collectJob.cancel()
        }

    @Test
    fun `should call signUpWithEmail and jump to ALL_SET with ACCOUNT_CREATED reason when sign-up submit has valid fields`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.SignUpSubmitClicked)

            // Assert
            assertEquals(1, authRepository.signUpCallCount)
            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.ALL_SET, state.step)
            assertEquals(AllSetReason.ACCOUNT_CREATED, state.allSetReason)
            assertFalse(state.signUpForm.isSubmitting)
        }

    @Test
    fun `should set repeatPasswordError without calling the repository when sign-up submit has a mismatched repeat password`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            viewModel.onIntent(OnboardingIntent.SignUpRepeatPasswordChanged("mismatch"))

            // Act
            viewModel.onIntent(OnboardingIntent.SignUpSubmitClicked)

            // Assert
            assertTrue(viewModel.uiState.value.signUpForm.repeatPasswordError)
            assertEquals(0, authRepository.signUpCallCount)
        }

    @Test
    fun `should emit a ShowError event and stop submitting when sign-up submit fails`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            authRepository.signUpError = AuthError.EmailAlreadyRegistered
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingIntent.SignUpSubmitClicked)
            runCurrent()

            // Assert
            assertEquals(
                listOf(OnboardingUiEvent.ShowError(AuthErrorUiState.EMAIL_ALREADY_REGISTERED)),
                events,
            )
            assertFalse(viewModel.uiState.value.signUpForm.isSubmitting)
            collectJob.cancel()
        }

    @Test
    fun `should emit a ShowError event with Unknown when GoogleSignInFailed is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingIntent.GoogleSignInFailed)
            runCurrent()

            // Assert
            assertEquals(listOf(OnboardingUiEvent.ShowError(AuthErrorUiState.UNKNOWN)), events)
            collectJob.cancel()
        }

    @Test
    fun `should advance to ALL_SET with NO_ACCOUNT reason when NextClicked on ACCOUNT_INFO with nothing chosen`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToAccountInfo(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Assert
            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.ALL_SET, state.step)
            assertEquals(AllSetReason.NO_ACCOUNT, state.allSetReason)
        }

    @Test
    fun `should advance to ALL_SET when NextClicked on ACCOUNT_INFO while mid sign-in form`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            advanceToSignIn(viewModel)
            viewModel.onIntent(OnboardingIntent.SignInEmailChanged("person@example.com"))

            // Act
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Assert
            assertEquals(OnboardingStep.ALL_SET, viewModel.uiState.value.step)
        }

    @Test
    fun `should reflect a session that becomes signed in in authState`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedOut)
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)

            // Act
            authRepository.emit(AuthState.SignedIn(userId = "u1", email = "person@example.com"))

            // Assert
            assertEquals(
                AuthStateUi.SignedIn(email = "person@example.com"),
                viewModel.uiState.value.authState,
            )
        }

    @Test
    fun `should emit Finished when NextClicked on ALL_SET`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingIntent.NextClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(OnboardingUiEvent.Finished), events)
            collectJob.cancel()
        }

    @Test
    fun `should return to WELCOME when StepBack from FOCUS_PICK`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Act
            viewModel.onIntent(OnboardingIntent.StepBack)

            // Assert
            assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.step)
        }

    @Test
    fun `should return to FOCUS_PICK and pre-fill confirmedFocus when StepBack from ACCOUNT_INFO`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Act
            viewModel.onIntent(OnboardingIntent.StepBack)

            // Assert
            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.FOCUS_PICK, state.step)
            assertEquals(FocusUiState.HABIT, state.selectedFocus)
        }

    @Test
    fun `should return to ACCOUNT_INFO when StepBack from ALL_SET`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.HABIT))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.NextClicked)

            // Act
            viewModel.onIntent(OnboardingIntent.StepBack)

            // Assert
            assertEquals(OnboardingStep.ACCOUNT_INFO, viewModel.uiState.value.step)
        }

    @Test
    fun `should emit ExitApp without changing step when StepBack from WELCOME`() =
        runTest {
            // Arrange
            val viewModel = viewModel(FakeOnboardingRepository())
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingIntent.StepBack)
            runCurrent()

            // Assert
            assertEquals(listOf(OnboardingUiEvent.ExitApp), events)
            assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.step)
            collectJob.cancel()
        }

    @Test
    fun `should call SkipOnboardingUseCase and emit Finished when SkipClicked with no focus confirmed yet`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val viewModel = viewModel(repository)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingIntent.SkipClicked)
            runCurrent()

            // Assert
            assertEquals(1, repository.saveFocusCallCount)
            assertEquals(listOf(OnboardingUiEvent.Finished), events)
            collectJob.cancel()
        }

    @Test
    fun `should emit Finished with no use-case call when SkipClicked with a focus already confirmed`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(OnboardingIntent.NextClicked)
            viewModel.onIntent(OnboardingIntent.FocusOptionSelected(FocusUiState.JOURNAL))
            viewModel.onIntent(OnboardingIntent.NextClicked)
            val events = mutableListOf<OnboardingUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingIntent.SkipClicked)
            runCurrent()

            // Assert
            assertEquals(1, repository.saveFocusCallCount)
            assertEquals(FocusUiState.JOURNAL, viewModel.uiState.value.confirmedFocus)
            assertEquals(listOf(OnboardingUiEvent.Finished), events)
            collectJob.cancel()
        }

    @Test
    fun `should show DATA_SYNC_REVIEW with counts when sign-up succeeds with unsynced local data present`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry()))
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository, journalRepository = journalRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.SignUpSubmitClicked)

            // Assert
            val state = viewModel.uiState.value
            assertEquals(AccountSubStep.DATA_SYNC_REVIEW, state.accountSubStep)
            assertEquals(OnboardingStep.ACCOUNT_INFO, state.step)
            assertEquals(1, state.dataSyncSummary?.journalEntryCount)
        }

    @Test
    fun `should jump straight to ALL_SET when sign-up succeeds with no local data`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(FakeOnboardingRepository(), authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)

            // Act
            viewModel.onIntent(OnboardingIntent.SignUpSubmitClicked)

            // Assert
            assertEquals(OnboardingStep.ALL_SET, viewModel.uiState.value.step)
        }

    @Test
    fun `should mark data synced and reach ALL_SET with ACCOUNT_CREATED reason when SyncConfirmClicked succeeds after sign-up`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry()))
            val onboardingRepository = FakeOnboardingRepository()
            val viewModel = viewModel(onboardingRepository, authRepository, journalRepository = journalRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            viewModel.onIntent(OnboardingIntent.SignUpSubmitClicked)
            assertEquals(AccountSubStep.DATA_SYNC_REVIEW, viewModel.uiState.value.accountSubStep)

            // Act
            viewModel.onIntent(OnboardingIntent.SyncConfirmClicked)
            runCurrent()

            // Assert
            assertEquals(1, onboardingRepository.markLocalDataSyncedCallCount)
            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.ALL_SET, state.step)
            assertEquals(AllSetReason.ACCOUNT_CREATED, state.allSetReason)
        }

    @Test
    fun `should reach ALL_SET without marking synced when SyncSkipClicked after sign-up`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry()))
            val onboardingRepository = FakeOnboardingRepository()
            val viewModel = viewModel(onboardingRepository, authRepository, journalRepository = journalRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            viewModel.onIntent(OnboardingIntent.SignUpSubmitClicked)

            // Act
            viewModel.onIntent(OnboardingIntent.SyncSkipClicked)

            // Assert
            assertEquals(0, onboardingRepository.markLocalDataSyncedCallCount)
            val state = viewModel.uiState.value
            assertEquals(OnboardingStep.ALL_SET, state.step)
            assertEquals(AllSetReason.ACCOUNT_CREATED, state.allSetReason)
        }

    private fun entry() = JournalEntry(
        id = "1",
        date = LocalDate.of(2026, 8, 1),
        text = "text",
        createdAt = Instant.EPOCH,
    )
}
