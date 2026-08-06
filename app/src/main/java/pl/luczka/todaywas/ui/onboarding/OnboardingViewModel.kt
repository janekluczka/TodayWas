package pl.luczka.todaywas.ui.onboarding

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
import pl.luczka.todaywas.domain.usecase.SelectFocusUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SkipOnboardingUseCase
import pl.luczka.todaywas.ui.auth.AuthFormMode
import pl.luczka.todaywas.ui.auth.AuthFormUiState
import pl.luczka.todaywas.ui.auth.isValidEmail
import pl.luczka.todaywas.ui.auth.isValidName
import pl.luczka.todaywas.ui.auth.isValidPassword
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.toDomain
import pl.luczka.todaywas.ui.model.toUiState
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val selectFocus: SelectFocusUseCase,
    private val skipOnboarding: SkipOnboardingUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = OnboardingStep.WELCOME,
            selectedFocus = null,
            confirmedFocus = null,
            isSaving = false,
            saveError = false,
            accountSubStep = AccountSubStep.CHOICE,
            authState = AuthStateUi.Loading,
            authForm = AuthFormUiState(),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<OnboardingUiEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingUiEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeAuthState().collect { state ->
                _uiState.update { it.copy(authState = state.toUiState()) }
            }
        }
    }

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.NextClicked -> onNextClicked()
            OnboardingIntent.SkipClicked -> onSkipClicked()
            OnboardingIntent.StepBack -> onStepBack()
            is OnboardingIntent.FocusOptionSelected -> onFocusOptionSelected(intent.focus)
            OnboardingIntent.CreateAccountClicked -> onAccountChoiceSelected(AuthFormMode.SIGN_UP)
            OnboardingIntent.SignInClicked -> onAccountChoiceSelected(AuthFormMode.SIGN_IN)
            OnboardingIntent.BackToChoiceClicked -> onBackToChoiceClicked()
            is OnboardingIntent.FirstNameChanged -> onFirstNameChanged(intent.value)
            is OnboardingIntent.LastNameChanged -> onLastNameChanged(intent.value)
            is OnboardingIntent.EmailChanged -> onEmailChanged(intent.value)
            is OnboardingIntent.PasswordChanged -> onPasswordChanged(intent.value)
            OnboardingIntent.ModeToggled -> onModeToggled()
            OnboardingIntent.SubmitClicked -> onSubmitClicked()
            is OnboardingIntent.GoogleIdTokenReceived -> onGoogleIdTokenReceived(intent.idToken)
            OnboardingIntent.GoogleSignInFailed -> onGoogleSignInFailed()
        }
    }

    private fun onNextClicked() {
        when (_uiState.value.step) {
            OnboardingStep.WELCOME -> _uiState.update { it.copy(step = OnboardingStep.FOCUS_PICK) }
            OnboardingStep.FOCUS_PICK -> onConfirmFocus()
            OnboardingStep.ACCOUNT_INFO -> _uiState.update { it.copy(step = OnboardingStep.ALL_SET) }
            OnboardingStep.ALL_SET -> eventChannel.trySend(OnboardingUiEvent.Finished)
        }
    }

    private fun onConfirmFocus() {
        if (_uiState.value.isSaving) return
        val focus = _uiState.value.selectedFocus ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = selectFocus(focus.toDomain())
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        isSaving = false,
                        confirmedFocus = focus,
                        step = OnboardingStep.ACCOUNT_INFO,
                    )
                } else {
                    current.copy(
                        isSaving = false,
                        saveError = true,
                    )
                }
            }
        }
    }

    private fun onFocusOptionSelected(focus: FocusUiState) {
        _uiState.update { it.copy(selectedFocus = focus) }
    }

    private fun onStepBack() {
        when (_uiState.value.step) {
            OnboardingStep.FOCUS_PICK -> _uiState.update { it.copy(step = OnboardingStep.WELCOME) }
            OnboardingStep.ACCOUNT_INFO ->
                _uiState.update {
                    it.copy(
                        step = OnboardingStep.FOCUS_PICK,
                        selectedFocus = it.confirmedFocus,
                    )
                }
            OnboardingStep.ALL_SET -> _uiState.update { it.copy(step = OnboardingStep.ACCOUNT_INFO) }
            OnboardingStep.WELCOME -> eventChannel.trySend(OnboardingUiEvent.ExitApp)
        }
    }

    private fun onSkipClicked() {
        if (_uiState.value.isSaving) return
        if (_uiState.value.confirmedFocus != null) {
            eventChannel.trySend(OnboardingUiEvent.Finished)
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = skipOnboarding()
            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveError = result.isFailure,
                )
            }
            if (result.isSuccess) {
                eventChannel.trySend(OnboardingUiEvent.Finished)
            }
        }
    }

    private fun onAccountChoiceSelected(mode: AuthFormMode) {
        _uiState.update {
            it.copy(
                accountSubStep = AccountSubStep.FORM,
                authForm = it.authForm.copy(mode = mode),
            )
        }
    }

    private fun onBackToChoiceClicked() {
        _uiState.update { it.copy(accountSubStep = AccountSubStep.CHOICE) }
    }

    private fun onFirstNameChanged(value: String) {
        _uiState.update { it.copy(authForm = it.authForm.copy(firstName = value, firstNameError = false)) }
    }

    private fun onLastNameChanged(value: String) {
        _uiState.update { it.copy(authForm = it.authForm.copy(lastName = value, lastNameError = false)) }
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
                    firstNameError = false,
                    lastNameError = false,
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
        val firstNameValid = form.mode == AuthFormMode.SIGN_IN || isValidName(form.firstName)
        val lastNameValid = form.mode == AuthFormMode.SIGN_IN || isValidName(form.lastName)
        if (!emailValid || !passwordValid || !firstNameValid || !lastNameValid) {
            _uiState.update {
                it.copy(
                    authForm = it.authForm.copy(
                        firstNameError = !firstNameValid,
                        lastNameError = !lastNameValid,
                        emailError = !emailValid,
                        passwordError = !passwordValid,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authForm = it.authForm.copy(isSubmitting = true)) }
            val result = when (form.mode) {
                AuthFormMode.SIGN_UP -> signUpWithEmail(form.email, form.password, form.firstName, form.lastName)
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
        eventChannel.trySend(OnboardingUiEvent.ShowError(AuthError.Unknown.toUiState()))
    }

    private fun applyAuthResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(authForm = AuthFormUiState()) }
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(authForm = it.authForm.copy(isSubmitting = false)) }
            eventChannel.trySend(OnboardingUiEvent.ShowError(error.toUiState()))
        }
    }
}
