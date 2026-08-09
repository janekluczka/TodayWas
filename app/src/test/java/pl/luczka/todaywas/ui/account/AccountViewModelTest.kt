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
    fun `initial state reflects the observed auth state and starts on SIGN_IN`() = runTest {
        val repository = FakeAuthRepository(initialState = AuthState.SignedOut)

        val viewModel = viewModel(repository)

        assertEquals(AuthStateUi.SignedOut, viewModel.uiState.value.authState)
        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
    }

    @Test
    fun `BackClicked emits NavigatedBack`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onIntent(AccountIntent.BackClicked)
        runCurrent()

        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        collectJob.cancel()
    }

    @Test
    fun `SignUpLinkClicked moves to SIGN_UP`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())

        viewModel.onIntent(AccountIntent.SignUpLinkClicked)

        assertEquals(AccountStep.SIGN_UP, viewModel.uiState.value.step)
    }

    @Test
    fun `BackClicked from SIGN_UP returns to SIGN_IN without navigating back`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onIntent(AccountIntent.BackClicked)
        runCurrent()

        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
        assertEquals(emptyList<AccountUiEvent>(), events)
        collectJob.cancel()
    }

    @Test
    fun `sign-in submit with invalid email sets emailError without calling the repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignInEmailChanged("not-an-email"))
        viewModel.onIntent(AccountIntent.SignInPasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SignInSubmitClicked)

        assertTrue(viewModel.uiState.value.signInForm.emailError)
        assertEquals(0, repository.signInCallCount)
    }

    @Test
    fun `sign-in submit with valid fields calls signInWithEmail and pops back without a SUCCESS step`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }
        viewModel.onIntent(AccountIntent.SignInEmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.SignInPasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SignInSubmitClicked)
        runCurrent()

        assertEquals(1, repository.signInCallCount)
        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        assertEquals(SignInFormUiState(), viewModel.uiState.value.signInForm)
        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
        collectJob.cancel()
    }

    @Test
    fun `sign-in submit failure emits a ShowError event and stops submitting without navigating back`() = runTest {
        val repository = FakeAuthRepository()
        repository.signInError = AuthError.InvalidCredentials
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }
        viewModel.onIntent(AccountIntent.SignInEmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.SignInPasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SignInSubmitClicked)
        runCurrent()

        assertEquals(
            listOf(AccountUiEvent.ShowError(AuthErrorUiState.INVALID_CREDENTIALS)),
            events,
        )
        assertFalse(viewModel.uiState.value.signInForm.isSubmitting)
        collectJob.cancel()
    }

    @Test
    fun `sign-up submit with valid fields calls signUpWithEmail and moves to SUCCESS`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        fillSignUpForm(viewModel)

        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)

        assertEquals(1, repository.signUpCallCount)
        assertEquals(AccountStep.SUCCESS, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.signUpForm.isSubmitting)
    }

    @Test
    fun `sign-up submit with mismatched repeat password sets repeatPasswordError without calling the repository`() =
        runTest {
            val repository = FakeAuthRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(AccountIntent.SignUpLinkClicked)
            fillSignUpForm(viewModel)
            viewModel.onIntent(AccountIntent.SignUpRepeatPasswordChanged("mismatch"))

            viewModel.onIntent(AccountIntent.SignUpSubmitClicked)

            assertTrue(viewModel.uiState.value.signUpForm.repeatPasswordError)
            assertEquals(0, repository.signUpCallCount)
        }

    @Test
    fun `sign-up submit failure emits a ShowError event with the mapped error and stops submitting`() = runTest {
        val repository = FakeAuthRepository()
        repository.signUpError = AuthError.EmailAlreadyRegistered
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }
        fillSignUpForm(viewModel)

        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)
        runCurrent()

        assertEquals(
            listOf(AccountUiEvent.ShowError(AuthErrorUiState.EMAIL_ALREADY_REGISTERED)),
            events,
        )
        assertFalse(viewModel.uiState.value.signUpForm.isSubmitting)
        collectJob.cancel()
    }

    @Test
    fun `ContinueClicked from SUCCESS emits NavigatedBack`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        fillSignUpForm(viewModel)
        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onIntent(AccountIntent.ContinueClicked)
        runCurrent()

        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        collectJob.cancel()
    }

    @Test
    fun `SignInGoogleIdTokenReceived calls signInWithGoogleIdToken and pops back on success`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onIntent(AccountIntent.SignInGoogleIdTokenReceived("id-token"))
        runCurrent()

        assertFalse(viewModel.uiState.value.signInForm.isSubmitting)
        assertEquals(listOf(AccountUiEvent.NavigatedBack), events)
        collectJob.cancel()
    }

    @Test
    fun `GoogleSignInFailed emits a ShowError event with Unknown`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onIntent(AccountIntent.GoogleSignInFailed)
        runCurrent()

        assertEquals(listOf(AccountUiEvent.ShowError(AuthErrorUiState.UNKNOWN)), events)
        collectJob.cancel()
    }

    @Test
    fun `SignOutClicked calls signOut`() = runTest {
        val repository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
        val viewModel = viewModel(repository)

        viewModel.onIntent(AccountIntent.SignOutClicked)

        assertEquals(1, repository.signOutCallCount)
    }

    @Test
    fun `SignOutClicked failure emits a ShowError event and clears isSigningOut`() = runTest {
        val repository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
        repository.signOutError = AuthError.NetworkUnavailable
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onIntent(AccountIntent.SignOutClicked)
        runCurrent()

        assertEquals(
            listOf(AccountUiEvent.ShowError(AuthErrorUiState.NETWORK_UNAVAILABLE)),
            events,
        )
        assertFalse(viewModel.uiState.value.isSigningOut)
        collectJob.cancel()
    }

    @Test
    fun `SignOutClicked resets a stale SUCCESS step back to SIGN_IN`() = runTest {
        val repository = FakeAuthRepository(initialState = AuthState.SignedIn(userId = "u1", email = "a@b.com"))
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.SignUpLinkClicked)
        fillSignUpForm(viewModel)
        viewModel.onIntent(AccountIntent.SignUpSubmitClicked)
        assertEquals(AccountStep.SUCCESS, viewModel.uiState.value.step)

        viewModel.onIntent(AccountIntent.SignOutClicked)

        assertEquals(AccountStep.SIGN_IN, viewModel.uiState.value.step)
    }
}
