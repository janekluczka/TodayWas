package pl.luczka.todaywas.ui.onboarding.accountsetup

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
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.repository.OnboardingRepository
import pl.luczka.todaywas.domain.usecase.CompleteOnboardingUseCase
import pl.luczka.todaywas.domain.usecase.GetLocalDataSummaryUseCase
import pl.luczka.todaywas.domain.usecase.MarkLocalDataSyncedUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.domain.usecase.ShouldReviewLocalDataBeforeSyncUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.onboarding.AccountSubStep
import pl.luczka.todaywas.ui.onboarding.AllSetReason
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingAccountSetupViewModelTest {

    private class FakeOnboardingRepository : OnboardingRepository {

        private val stateFlow = MutableStateFlow(
            OnboardingState(completed = false, hasSyncedLocalData = false),
        )

        var completeOnboardingCallCount = 0
            private set

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun completeOnboarding(): Result<Unit> {
            completeOnboardingCallCount++
            stateFlow.value = stateFlow.value.copy(completed = true)
            return Result.success(Unit)
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
        repository: OnboardingRepository = FakeOnboardingRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        journalRepository: FakeJournalRepository = FakeJournalRepository(),
        habitRepository: FakeHabitRepository = FakeHabitRepository(),
    ) = OnboardingAccountSetupViewModel(
        completeOnboarding = CompleteOnboardingUseCase(repository),
        observeAuthState = ObserveAuthStateUseCase(authRepository),
        observeOnboardingState = ObserveOnboardingStateUseCase(repository),
        signUpWithEmail = SignUpWithEmailUseCase(authRepository),
        signInWithEmail = SignInWithEmailUseCase(authRepository),
        signInWithGoogle = SignInWithGoogleUseCase(authRepository),
        getLocalDataSummary = GetLocalDataSummaryUseCase(journalRepository, habitRepository),
        syncLocalData = SyncLocalDataUseCase(journalRepository, habitRepository),
        markLocalDataSynced = MarkLocalDataSyncedUseCase(repository),
        shouldReviewLocalDataBeforeSync = ShouldReviewLocalDataBeforeSyncUseCase(),
    )

    private fun advanceToSignUp(viewModel: OnboardingAccountSetupViewModel) {
        viewModel.onIntent(OnboardingAccountSetupIntent.SignUpLinkClicked)
    }

    private fun fillSignUpForm(viewModel: OnboardingAccountSetupViewModel) {
        viewModel.onIntent(OnboardingAccountSetupIntent.SignUpEmailChanged("person@example.com"))
        viewModel.onIntent(OnboardingAccountSetupIntent.SignUpPasswordChanged("password123"))
        viewModel.onIntent(
            OnboardingAccountSetupIntent.SignUpRepeatPasswordChanged("password123"),
        )
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
    fun `should start on SIGN_IN`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            val state = viewModel.uiState.value

            // Assert
            assertEquals(AccountSubStep.SIGN_IN, state.accountSubStep)
        }

    @Test
    fun `should move to SIGN_UP when SignUpLinkClicked from SIGN_IN`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpLinkClicked)

            // Assert
            assertEquals(AccountSubStep.SIGN_UP, viewModel.uiState.value.accountSubStep)
        }

    @Test
    fun `should move to SIGN_IN when SignInLinkClicked from SIGN_UP`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            advanceToSignUp(viewModel)

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInLinkClicked)

            // Assert
            assertEquals(AccountSubStep.SIGN_IN, viewModel.uiState.value.accountSubStep)
        }

    @Test
    fun `should move to SIGN_IN when StepBack from SIGN_UP`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            advanceToSignUp(viewModel)

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.StepBack)

            // Assert
            assertEquals(AccountSubStep.SIGN_IN, viewModel.uiState.value.accountSubStep)
        }

    @Test
    fun `should emit NavigateBack when StepBack from SIGN_IN`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.StepBack)
            runCurrent()

            // Assert
            assertEquals(listOf(OnboardingAccountSetupUiEvent.NavigateBack), events)
            collectJob.cancel()
        }

    @Test
    fun `should call signInWithEmail and emit Finished with SIGNED_IN reason when sign-in submit has valid fields`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(repository, authRepository)
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }
            viewModel.onIntent(
                OnboardingAccountSetupIntent.SignInEmailChanged("person@example.com"),
            )
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInPasswordChanged("password123"))

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInSubmitClicked)
            runCurrent()

            // Assert
            assertEquals(1, authRepository.signInCallCount)
            assertEquals(1, repository.completeOnboardingCallCount)
            assertEquals(
                listOf(OnboardingAccountSetupUiEvent.Finished(AllSetReason.SIGNED_IN)),
                events,
            )
            assertFalse(viewModel.uiState.value.signInForm.isSubmitting)
            collectJob.cancel()
        }

    @Test
    fun `should set emailError without calling the repository when sign-in submit has an invalid email`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(authRepository = authRepository)
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInEmailChanged("not-an-email"))
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInPasswordChanged("password123"))

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInSubmitClicked)

            // Assert
            assertTrue(viewModel.uiState.value.signInForm.emailError)
            assertEquals(0, authRepository.signInCallCount)
        }

    @Test
    fun `should emit a ShowError event and stop submitting when sign-in submit fails`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            authRepository.signInError = AuthError.InvalidCredentials
            val viewModel = viewModel(authRepository = authRepository)
            viewModel.onIntent(
                OnboardingAccountSetupIntent.SignInEmailChanged("person@example.com"),
            )
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInPasswordChanged("password123"))
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignInSubmitClicked)
            runCurrent()

            // Assert
            assertEquals(
                listOf(
                    OnboardingAccountSetupUiEvent.ShowError(AuthErrorUiState.INVALID_CREDENTIALS),
                ),
                events,
            )
            assertFalse(viewModel.uiState.value.signInForm.isSubmitting)
            collectJob.cancel()
        }

    @Test
    fun `should call signUpWithEmail and emit Finished with ACCOUNT_CREATED reason when sign-up submit has valid fields`() =
        runTest {
            // Arrange
            val repository = FakeOnboardingRepository()
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(repository, authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)
            runCurrent()

            // Assert
            assertEquals(1, authRepository.signUpCallCount)
            assertEquals(
                listOf(OnboardingAccountSetupUiEvent.Finished(AllSetReason.ACCOUNT_CREATED)),
                events,
            )
            assertFalse(viewModel.uiState.value.signUpForm.isSubmitting)
            collectJob.cancel()
        }

    @Test
    fun `should set repeatPasswordError without calling the repository when sign-up submit has a mismatched repeat password`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(authRepository = authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpRepeatPasswordChanged("mismatch"))

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)

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
            val viewModel = viewModel(authRepository = authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)
            runCurrent()

            // Assert
            assertEquals(
                listOf(
                    OnboardingAccountSetupUiEvent.ShowError(
                        AuthErrorUiState.EMAIL_ALREADY_REGISTERED,
                    ),
                ),
                events,
            )
            assertFalse(viewModel.uiState.value.signUpForm.isSubmitting)
            collectJob.cancel()
        }

    @Test
    fun `should emit a ShowError event with Unknown when GoogleSignInFailed is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.GoogleSignInFailed)
            runCurrent()

            // Assert
            assertEquals(
                listOf(OnboardingAccountSetupUiEvent.ShowError(AuthErrorUiState.UNKNOWN)),
                events,
            )
            collectJob.cancel()
        }

    @Test
    fun `should reflect a session that becomes signed in in authState`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository(initialState = AuthState.SignedOut)
            val viewModel = viewModel(authRepository = authRepository)

            // Act
            authRepository.emit(AuthState.SignedIn(userId = "u1", email = "person@example.com"))

            // Assert
            assertEquals(
                AuthStateUi.SignedIn(email = "person@example.com"),
                viewModel.uiState.value.authState,
            )
        }

    @Test
    fun `should show DATA_SYNC_REVIEW with counts when sign-up succeeds with unsynced local data present`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry()))
            val viewModel = viewModel(
                authRepository = authRepository,
                journalRepository = journalRepository,
            )
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)

            // Assert
            val state = viewModel.uiState.value
            assertEquals(AccountSubStep.DATA_SYNC_REVIEW, state.accountSubStep)
            assertEquals(1, state.dataSyncSummary?.journalEntryCount)
        }

    @Test
    fun `should emit Finished when sign-up succeeds with no local data`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val viewModel = viewModel(authRepository = authRepository)
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)
            runCurrent()

            // Assert
            assertEquals(
                listOf(OnboardingAccountSetupUiEvent.Finished(AllSetReason.ACCOUNT_CREATED)),
                events,
            )
            collectJob.cancel()
        }

    @Test
    fun `should mark data synced and emit Finished with ACCOUNT_CREATED reason when SyncConfirmClicked succeeds after sign-up`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry()))
            val onboardingRepository = FakeOnboardingRepository()
            val viewModel = viewModel(
                onboardingRepository,
                authRepository,
                journalRepository = journalRepository,
            )
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)
            assertEquals(AccountSubStep.DATA_SYNC_REVIEW, viewModel.uiState.value.accountSubStep)
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SyncConfirmClicked)
            runCurrent()

            // Assert
            assertEquals(1, onboardingRepository.markLocalDataSyncedCallCount)
            assertEquals(1, onboardingRepository.completeOnboardingCallCount)
            assertEquals(
                listOf(OnboardingAccountSetupUiEvent.Finished(AllSetReason.ACCOUNT_CREATED)),
                events,
            )
            collectJob.cancel()
        }

    @Test
    fun `should show an error and stay on DATA_SYNC_REVIEW without marking synced when SyncConfirmClicked's sync fails`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry()))
            journalRepository.syncWithRemoteResult =
                Result.failure(RuntimeException("network error"))
            val onboardingRepository = FakeOnboardingRepository()
            val viewModel = viewModel(
                onboardingRepository,
                authRepository,
                journalRepository = journalRepository,
            )
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)
            assertEquals(AccountSubStep.DATA_SYNC_REVIEW, viewModel.uiState.value.accountSubStep)
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SyncConfirmClicked)
            runCurrent()

            // Assert
            assertEquals(
                listOf(OnboardingAccountSetupUiEvent.ShowError(AuthErrorUiState.UNKNOWN)),
                events,
            )
            assertEquals(0, onboardingRepository.markLocalDataSyncedCallCount)
            assertEquals(0, onboardingRepository.completeOnboardingCallCount)
            assertEquals(AccountSubStep.DATA_SYNC_REVIEW, viewModel.uiState.value.accountSubStep)
            assertTrue(viewModel.uiState.value.dataSyncSummary != null)
            assertFalse(viewModel.uiState.value.isSyncing)
            collectJob.cancel()
        }

    @Test
    fun `should emit Finished without marking synced when SyncSkipClicked after sign-up`() =
        runTest {
            // Arrange
            val authRepository = FakeAuthRepository()
            val journalRepository = FakeJournalRepository(initialEntries = listOf(entry()))
            val onboardingRepository = FakeOnboardingRepository()
            val viewModel = viewModel(
                onboardingRepository,
                authRepository,
                journalRepository = journalRepository,
            )
            advanceToSignUp(viewModel)
            fillSignUpForm(viewModel)
            viewModel.onIntent(OnboardingAccountSetupIntent.SignUpSubmitClicked)
            val events = mutableListOf<OnboardingAccountSetupUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(OnboardingAccountSetupIntent.SyncSkipClicked)
            runCurrent()

            // Assert
            assertEquals(0, onboardingRepository.markLocalDataSyncedCallCount)
            assertEquals(1, onboardingRepository.completeOnboardingCallCount)
            assertEquals(
                listOf(OnboardingAccountSetupUiEvent.Finished(AllSetReason.ACCOUNT_CREATED)),
                events,
            )
            collectJob.cancel()
        }

    private fun entry() = JournalEntry(
        id = "1",
        date = LocalDate.of(2026, 8, 1),
        text = "text",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
