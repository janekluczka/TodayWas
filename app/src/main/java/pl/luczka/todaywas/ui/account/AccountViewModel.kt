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
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.auth.isValidEmail
import pl.luczka.todaywas.ui.auth.isValidPassword
import pl.luczka.todaywas.ui.auth.isValidRepeatPassword
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
            step = AccountStep.SIGN_IN,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
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
            AccountIntent.BackClicked -> onBackClicked()
            is AccountIntent.SignInEmailChanged -> onSignInEmailChanged(intent.value)
            is AccountIntent.SignInPasswordChanged -> onSignInPasswordChanged(intent.value)
            AccountIntent.SignInSubmitClicked -> onSignInSubmitClicked()
            is AccountIntent.SignInGoogleIdTokenReceived -> onSignInGoogleIdTokenReceived(intent.idToken)
            AccountIntent.GoogleSignInFailed -> onGoogleSignInFailed()
            AccountIntent.SignUpLinkClicked -> _uiState.update { it.copy(step = AccountStep.SIGN_UP) }
            is AccountIntent.SignUpEmailChanged -> onSignUpEmailChanged(intent.value)
            is AccountIntent.SignUpPasswordChanged -> onSignUpPasswordChanged(intent.value)
            is AccountIntent.SignUpRepeatPasswordChanged -> onSignUpRepeatPasswordChanged(intent.value)
            AccountIntent.SignUpSubmitClicked -> onSignUpSubmitClicked()
            AccountIntent.ContinueClicked -> eventChannel.trySend(AccountUiEvent.NavigatedBack)
            AccountIntent.SignOutClicked -> onSignOutClicked()
        }
    }

    private fun onBackClicked() {
        if (_uiState.value.step == AccountStep.SIGN_UP) {
            _uiState.update { it.copy(step = AccountStep.SIGN_IN) }
        } else {
            eventChannel.trySend(AccountUiEvent.NavigatedBack)
        }
    }

    private fun onSignInEmailChanged(value: String) {
        _uiState.update { it.copy(signInForm = it.signInForm.copy(email = value, emailError = false)) }
    }

    private fun onSignInPasswordChanged(value: String) {
        _uiState.update { it.copy(signInForm = it.signInForm.copy(password = value, passwordError = false)) }
    }

    private fun onSignInSubmitClicked() {
        val form = _uiState.value.signInForm
        if (form.isSubmitting) return

        val emailValid = isValidEmail(form.email)
        val passwordValid = isValidPassword(form.password)
        if (!emailValid || !passwordValid) {
            _uiState.update {
                it.copy(
                    signInForm = it.signInForm.copy(
                        emailError = !emailValid,
                        passwordError = !passwordValid,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = true)) }
            applySignInResult(signInWithEmail(form.email, form.password))
        }
    }

    private fun onSignInGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = true)) }
            applySignInResult(signInWithGoogle(idToken))
        }
    }

    private fun onGoogleSignInFailed() {
        eventChannel.trySend(AccountUiEvent.ShowError(AuthError.Unknown.toUiState()))
    }

    private fun applySignInResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(signInForm = SignInFormUiState()) }
            eventChannel.trySend(AccountUiEvent.NavigatedBack)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = false)) }
            eventChannel.trySend(AccountUiEvent.ShowError(error.toUiState()))
        }
    }

    private fun onSignUpEmailChanged(value: String) {
        _uiState.update { it.copy(signUpForm = it.signUpForm.copy(email = value, emailError = false)) }
    }

    private fun onSignUpPasswordChanged(value: String) {
        _uiState.update { it.copy(signUpForm = it.signUpForm.copy(password = value, passwordError = false)) }
    }

    private fun onSignUpRepeatPasswordChanged(value: String) {
        _uiState.update {
            it.copy(signUpForm = it.signUpForm.copy(repeatPassword = value, repeatPasswordError = false))
        }
    }

    private fun onSignUpSubmitClicked() {
        val form = _uiState.value.signUpForm
        if (form.isSubmitting) return

        val emailValid = isValidEmail(form.email)
        val passwordValid = isValidPassword(form.password)
        val repeatPasswordValid = isValidRepeatPassword(form.password, form.repeatPassword)
        if (!emailValid || !passwordValid || !repeatPasswordValid) {
            _uiState.update {
                it.copy(
                    signUpForm = it.signUpForm.copy(
                        emailError = !emailValid,
                        passwordError = !passwordValid,
                        repeatPasswordError = !repeatPasswordValid,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = true)) }
            val result = signUpWithEmail(form.email, form.password)
            applySignUpResult(result)
        }
    }

    private fun applySignUpResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    signUpForm = SignUpFormUiState(),
                    step = AccountStep.SUCCESS,
                )
            }
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = false)) }
            eventChannel.trySend(AccountUiEvent.ShowError(error.toUiState()))
        }
    }

    private fun onSignOutClicked() {
        viewModelScope.launch {
            val result = signOut()
            if (result.isSuccess) {
                _uiState.update { it.copy(step = AccountStep.SIGN_IN) }
            }
        }
    }
}
