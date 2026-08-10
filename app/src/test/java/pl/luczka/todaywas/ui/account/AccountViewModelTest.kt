package pl.luczka.todaywas.ui.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import pl.luczka.todaywas.data.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private fun viewModel(repository: FakeAuthRepository) = AccountViewModel(
        observeAuthState = ObserveAuthStateUseCase(repository),
        signUpWithEmail = SignUpWithEmailUseCase(repository),
        signInWithEmail = SignInWithEmailUseCase(repository),
        signInWithGoogle = SignInWithGoogleUseCase(repository),
        signOut = SignOutUseCase(repository),
    )

    private fun fillSignUpForm(viewModel: AccountViewModel) {
        viewModel.onIntent(AccountIntent.SignUpEmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.SignUpPasswordChanged("password123"))
        viewModel.onIntent(AccountIntent.SignUpRepeatPasswordChanged("password123"))
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
    fun `should reflect the observed auth state and start on SIGN_IN on initial state`() = runTest {
        // Arrange
        val repository = FakeAuthRepository(initialState = AuthState.SignedOut)

        // Act
        val viewModel = viewModel(repository)

        // Assert
        assertEquals(AuthStateUi.SignedOut, viewModel.uiState.value.authState)
        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
    }

    @Test
    fun `should emit NavigatedBack when BackClicked is dispatched`() = runTest {
        // Arrange
        val viewModel = viewModel(FakeAuthRepository())
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        // Act
        viewModel.onIntent(AccountIntent.BackClicked)
        runCurrent()

        // Assert
        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        collectJob.cancel()
    }

    @Test
    fun `should move to SIGN_UP when SignUpLinkClicked is dispatched`() = runTest {
        // Arrange
        val viewModel = viewModel(FakeAuthRepository())

        // Act
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)

        // Assert
        assertEquals(AccountStep.SIGN_UP, viewModel.uiState.value.step)
    }

    @Test
    fun `should return to SIGN_IN without navigating back when BackClicked from SIGN_UP`() = runTest {
        // Arrange
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        // Act
        viewModel.onIntent(AccountIntent.BackClicked)
        runCurrent()

        // Assert
        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
        assertEquals(emptyList<AccountUiEvent>(), events)
        collectJob.cancel()
    }

    @Test
    fun `should set emailError without calling the repository when sign-in submit has an invalid email`() = runTest {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignInEmailChanged("not-an-email"))
        viewModel.onIntent(AccountIntent.SignInPasswordChanged("password123"))

        // Act
        viewModel.onIntent(AccountIntent.SignInSubmitClicked)

        // Assert
        assertTrue(viewModel.uiState.value.signInForm.emailError)
        assertEquals(0, repository.signInCallCount)
    }

    @Test
    fun `should call signInWithEmail and pop back without a SUCCESS step when sign-in submit has valid fields`() = runTest {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }
        viewModel.onIntent(AccountIntent.SignInEmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.SignInPasswordChanged("password123"))

        // Act
        viewModel.onIntent(AccountIntent.SignInSubmitClicked)
        runCurrent()

        // Assert
        assertEquals(1, repository.signInCallCount)
        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        assertEquals(SignInFormUiState(), viewModel.uiState.value.signInForm)
        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
        collectJob.cancel()
    }

    @Test
    fun `should emit a ShowError event and stop submitting without navigating back when sign-in submit fails`() = runTest {
        // Arrange
        val repository = FakeAuthRepository()
        repository.signInError = AuthError.InvalidCredentials
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }
        viewModel.onIntent(AccountIntent.SignInEmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.SignInPasswordChanged("password123"))

        // Act
        viewModel.onIntent(AccountIntent.SignInSubmitClicked)
        runCurrent()

        // Assert
        assertEquals(
            listOf(AccountUiEvent.ShowError(AuthErrorUiState.INVALID_CREDENTIALS)),
            events,
        )
        assertFalse(viewModel.uiState.value.signInForm.isSubmitting)
        collectJob.cancel()
    }

    @Test
    fun `should call signUpWithEmail and move to SUCCESS when sign-up submit has valid fields`() = runTest {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        fillSignUpForm(viewModel)

        // Act
        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)

        // Assert
        assertEquals(1, repository.signUpCallCount)
        assertEquals(AccountStep.SUCCESS, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.signUpForm.isSubmitting)
    }

    @Test
    fun `should set repeatPasswordError without calling the repository when sign-up submit has a mismatched repeat password`() =
        runTest {
            // Arrange
            val repository = FakeAuthRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(AccountIntent.SignUpLinkClicked)
            fillSignUpForm(viewModel)
            viewModel.onIntent(AccountIntent.SignUpRepeatPasswordChanged("mismatch"))

            // Act
            viewModel.onIntent(AccountIntent.SignUpSubmitClicked)

            // Assert
            assertTrue(viewModel.uiState.value.signUpForm.repeatPasswordError)
            assertEquals(0, repository.signUpCallCount)
        }

    @Test
    fun `should emit a ShowError event with the mapped error and stop submitting when sign-up submit fails`() = runTest {
        // Arrange
        val repository = FakeAuthRepository()
        repository.signUpError = AuthError.EmailAlreadyRegistered
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }
        fillSignUpForm(viewModel)

        // Act
        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)
        runCurrent()

        // Assert
        assertEquals(
            listOf(AccountUiEvent.ShowError(AuthErrorUiState.EMAIL_ALREADY_REGISTERED)),
            events,
        )
        assertFalse(viewModel.uiState.value.signUpForm.isSubmitting)
        collectJob.cancel()
    }

    @Test
    fun `should emit NavigatedBack when ContinueClicked from SUCCESS`() = runTest {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        fillSignUpForm(viewModel)
        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        // Act
        viewModel.onIntent(AccountIntent.ContinueClicked)
        runCurrent()

        // Assert
        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        collectJob.cancel()
    }

    @Test
    fun `should call signInWithGoogleIdToken and pop back on success when SignInGoogleIdTokenReceived is dispatched`() = runTest {
        // Arrange
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        // Act
        viewModel.onIntent(AccountIntent.SignInGoogleIdTokenReceived("id-token"))
        runCurrent()

        // Assert
        assertFalse(viewModel.uiState.value.signInForm.isSubmitting)
        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        collectJob.cancel()
    }

    @Test
    fun `should emit a ShowError event with Unknown when GoogleSignInFailed is dispatched`() = runTest {
        // Arrange
        val viewModel = viewModel(FakeAuthRepository())
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        // Act
        viewModel.onIntent(AccountIntent.GoogleSignInFailed)
        runCurrent()

        // Assert
        assertEquals(listOf(AccountUiEvent.ShowError(AuthErrorUiState.UNKNOWN)), events)
        collectJob.cancel()
    }

    @Test
    fun `should call signOut when SignOutClicked is dispatched`() = runTest {
        // Arrange
        val repository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
        val viewModel = viewModel(repository)

        // Act
        viewModel.onIntent(AccountIntent.SignOutClicked)

        // Assert
        assertEquals(1, repository.signOutCallCount)
    }

    @Test
    fun `should emit a ShowError event and clear isSigningOut when SignOutClicked fails`() = runTest {
        // Arrange
        val repository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
        repository.signOutError = AuthError.NetworkUnavailable
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        // Act
        viewModel.onIntent(AccountIntent.SignOutClicked)
        runCurrent()

        // Assert
        assertEquals(
            listOf(AccountUiEvent.ShowError(AuthErrorUiState.NETWORK_UNAVAILABLE)),
            events,
        )
        assertFalse(viewModel.uiState.value.isSigningOut)
        collectJob.cancel()
    }

    @Test
    fun `should reset a stale SUCCESS step back to SIGN_IN when SignOutClicked is dispatched`() = runTest {
        // Arrange
        val repository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        fillSignUpForm(viewModel)
        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)
        assertEquals(AccountStep.SUCCESS, viewModel.uiState.value.step)

        // Act
        viewModel.onIntent(AccountIntent.SignOutClicked)

        // Assert
        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
    }
}
