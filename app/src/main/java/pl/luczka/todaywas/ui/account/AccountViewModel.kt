package pl.luczka.todaywas.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.domain.model.AuthError
import pl.luczka.todaywas.domain.model.AuthException
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.ui.auth.AuthFormMode
import pl.luczka.todaywas.ui.auth.AuthFormUiState
import pl.luczka.todaywas.ui.auth.isValidEmail
import pl.luczka.todaywas.ui.auth.isValidPassword
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.toUiState
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountUiState(
            authState = AuthStateUi.Loading,
            authForm = AuthFormUiState(),
        ),
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<AccountUiEvent>(Channel.BUFFERED)
    val events: Flow<AccountUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeAuthState().collect { state ->
                _uiState.update { it.copy(authState = state.toUiState()) }
            }
        }
    }

    fun onIntent(intent: AccountIntent) {
        when (intent) {
            AccountIntent.BackClicked -> eventChannel.trySend(AccountUiEvent.NavigatedBack)
            is AccountIntent.EmailChanged -> onEmailChanged(intent.value)
            is AccountIntent.PasswordChanged -> onPasswordChanged(intent.value)
            AccountIntent.ModeToggled -> onModeToggled()
            AccountIntent.SubmitClicked -> onSubmitClicked()
            is AccountIntent.GoogleIdTokenReceived -> onGoogleIdTokenReceived(intent.idToken)
            AccountIntent.GoogleSignInFailed -> onGoogleSignInFailed()
            AccountIntent.SignOutClicked -> onSignOutClicked()
        }
    }

    private fun onEmailChanged(value: String) {
        _uiState.update { it.copy(authForm = it.authForm.copy(email = value, emailError = false)) }
    }

    private fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(authForm = it.authForm.copy(password = value, passwordError = false)) }
    }

    private fun onModeToggled() {
        _uiState.update {
            val nextMode = if (it.authForm.mode == AuthFormMode.SIGN_UP) AuthFormMode.SIGN_IN else AuthFormMode.SIGN_UP
            it.copy(
                authForm = it.authForm.copy(
                    mode = nextMode,
                    emailError = false,
                    passwordError = false,
                ),
            )
        }
    }

    private fun onSubmitClicked() {
        val form = _uiState.value.authForm
        if (form.isSubmitting) return

        val emailValid = isValidEmail(form.email)
        val passwordValid = isValidPassword(form.password)
        if (!emailValid || !passwordValid) {
            _uiState.update {
                it.copy(authForm = it.authForm.copy(emailError = !emailValid, passwordError = !passwordValid))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authForm = it.authForm.copy(isSubmitting = true)) }
            val result = when (form.mode) {
                AuthFormMode.SIGN_UP -> signUpWithEmail(form.email, form.password)
                AuthFormMode.SIGN_IN -> signInWithEmail(form.email, form.password)
            }
            applyAuthResult(result)
        }
    }

    private fun onGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(authForm = it.authForm.copy(isSubmitting = true)) }
            applyAuthResult(signInWithGoogle(idToken))
        }
    }

    private fun onGoogleSignInFailed() {
        eventChannel.trySend(AccountUiEvent.ShowError(AuthError.Unknown.toUiState()))
    }

    private fun onSignOutClicked() {
        viewModelScope.launch { signOut() }
    }

    private fun applyAuthResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(authForm = AuthFormUiState()) }
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(authForm = it.authForm.copy(isSubmitting = false)) }
            eventChannel.trySend(AccountUiEvent.ShowError(error.toUiState()))
        }
    }
}
