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
import pl.luczka.todaywas.ui.auth.AuthFormMode
import pl.luczka.todaywas.ui.auth.AuthFormUiState
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects the observed auth state`() = runTest {
        val repository = FakeAuthRepository(initialState = AuthState.SignedOut)

        val viewModel = viewModel(repository)

        assertEquals(AuthStateUi.SignedOut, viewModel.uiState.value.authState)
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
    fun `submit with invalid email sets emailError without calling the repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.EmailChanged("not-an-email"))
        viewModel.onIntent(AccountIntent.PasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SubmitClicked)

        assertTrue(viewModel.uiState.value.authForm.emailError)
        assertEquals(0, repository.signUpCallCount)
    }

    @Test
    fun `submit in SIGN_UP mode with valid fields calls signUpWithEmail and resets the form on success`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.FirstNameChanged("Jane"))
        viewModel.onIntent(AccountIntent.LastNameChanged("Doe"))
        viewModel.onIntent(AccountIntent.EmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.PasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SubmitClicked)

        assertEquals(1, repository.signUpCallCount)
        assertEquals("Jane", repository.lastSignUpFirstName)
        assertEquals("Doe", repository.lastSignUpLastName)
        assertEquals(AuthFormMode.SIGN_UP, viewModel.uiState.value.authForm.mode)
        assertEquals("", viewModel.uiState.value.authForm.email)
        assertFalse(viewModel.uiState.value.authForm.isSubmitting)
    }

    @Test
    fun `submit in SIGN_UP mode without a name sets name errors without calling the repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.EmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.PasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SubmitClicked)

        val form = viewModel.uiState.value.authForm
        assertTrue(form.firstNameError)
        assertTrue(form.lastNameError)
        assertEquals(0, repository.signUpCallCount)
    }

    @Test
    fun `submit in SIGN_IN mode calls signInWithEmail`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.onIntent(AccountIntent.ModeToggled)
        viewModel.onIntent(AccountIntent.EmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.PasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SubmitClicked)

        assertEquals(1, repository.signInCallCount)
        assertEquals(0, repository.signUpCallCount)
    }

    @Test
    fun `submit failure emits a ShowError event with the mapped error and stops submitting`() = runTest {
        val repository = FakeAuthRepository()
        repository.signUpError = AuthError.EmailAlreadyRegistered
        val viewModel = viewModel(repository)
        val events = mutableListOf<AccountUiEvent>()
        val collectJob = launch { viewModel.events.collect { events.add(it) } }
        viewModel.onIntent(AccountIntent.FirstNameChanged("Jane"))
        viewModel.onIntent(AccountIntent.LastNameChanged("Doe"))
        viewModel.onIntent(AccountIntent.EmailChanged("person@example.com"))
        viewModel.onIntent(AccountIntent.PasswordChanged("password123"))

        viewModel.onIntent(AccountIntent.SubmitClicked)
        runCurrent()

        assertEquals(
            listOf(AccountUiEvent.ShowError(AuthErrorUiState.EMAIL_ALREADY_REGISTERED)),
            events,
        )
        assertFalse(viewModel.uiState.value.authForm.isSubmitting)
        collectJob.cancel()
    }

    @Test
    fun `ModeToggled switches between SIGN_UP and SIGN_IN and clears errors`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.onIntent(AccountIntent.EmailChanged("not-an-email"))
        viewModel.onIntent(AccountIntent.SubmitClicked)
        assertTrue(viewModel.uiState.value.authForm.emailError)

        viewModel.onIntent(AccountIntent.ModeToggled)

        val form = viewModel.uiState.value.authForm
        assertEquals(AuthFormMode.SIGN_IN, form.mode)
        assertFalse(form.emailError)
    }

    @Test
    fun `GoogleIdTokenReceived calls signInWithGoogleIdToken and resets the form on success`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)

        viewModel.onIntent(AccountIntent.GoogleIdTokenReceived("id-token"))

        assertFalse(viewModel.uiState.value.authForm.isSubmitting)
        assertEquals(AuthFormUiState(), viewModel.uiState.value.authForm)
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
}
