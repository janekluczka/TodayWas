package pl.luczka.todaywas.ui.onboarding.accountsetup

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
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.auth.util.isValidEmail
import pl.luczka.todaywas.ui.auth.util.isValidPassword
import pl.luczka.todaywas.ui.auth.util.isValidRepeatPassword
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.onboarding.AccountSubStep
import pl.luczka.todaywas.ui.onboarding.AllSetReason
import javax.inject.Inject

@HiltViewModel
class OnboardingAccountSetupViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    observeOnboardingState: ObserveOnboardingStateUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val getLocalDataSummary: GetLocalDataSummaryUseCase,
    private val syncLocalData: SyncLocalDataUseCase,
    private val markLocalDataSynced: MarkLocalDataSyncedUseCase,
    private val shouldReviewLocalDataBeforeSync: ShouldReviewLocalDataBeforeSyncUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingAccountSetupUiState())
    val uiState: StateFlow<OnboardingAccountSetupUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<OnboardingAccountSetupUiEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingAccountSetupUiEvent> = eventChannel.receiveAsFlow()

    private var pendingAllSetReason = AllSetReason.NO_ACCOUNT

    init {
        viewModelScope.launch {
            observeAuthState().collect { state ->
                _uiState.update { it.copy(authState = state.toUiState()) }
            }
        }
        viewModelScope.launch {
            observeOnboardingState().collect { state ->
                _uiState.update { it.copy(hasSyncedLocalData = state.hasSyncedLocalData) }
            }
        }
    }

    fun onIntent(intent: OnboardingAccountSetupIntent) {
        when (intent) {
            OnboardingAccountSetupIntent.StepBack -> onStepBack()
            is OnboardingAccountSetupIntent.SignInEmailChanged ->
                onSignInEmailChanged(intent.value)
            is OnboardingAccountSetupIntent.SignInPasswordChanged ->
                onSignInPasswordChanged(intent.value)
            OnboardingAccountSetupIntent.SignInSubmitClicked -> onSignInSubmitClicked()
            is OnboardingAccountSetupIntent.SignInGoogleIdTokenReceived ->
                onSignInGoogleIdTokenReceived(intent.idToken)
            OnboardingAccountSetupIntent.GoogleSignInFailed -> onGoogleSignInFailed()
            OnboardingAccountSetupIntent.SignUpLinkClicked -> _uiState.update {
                it.copy(accountSubStep = AccountSubStep.SIGN_UP)
            }
            OnboardingAccountSetupIntent.SignInLinkClicked -> _uiState.update {
                it.copy(accountSubStep = AccountSubStep.SIGN_IN)
            }
            is OnboardingAccountSetupIntent.SignUpEmailChanged ->
                onSignUpEmailChanged(intent.value)
            is OnboardingAccountSetupIntent.SignUpPasswordChanged ->
                onSignUpPasswordChanged(intent.value)
            is OnboardingAccountSetupIntent.SignUpRepeatPasswordChanged ->
                onSignUpRepeatPasswordChanged(intent.value)
            OnboardingAccountSetupIntent.SignUpSubmitClicked -> onSignUpSubmitClicked()
            is OnboardingAccountSetupIntent.SignUpGoogleIdTokenReceived ->
                onSignUpGoogleIdTokenReceived(intent.idToken)
            OnboardingAccountSetupIntent.SyncConfirmClicked -> onSyncConfirmClicked()
            OnboardingAccountSetupIntent.SyncSkipClicked -> onSyncSkipClicked()
        }
    }

    // DATA_SYNC_REVIEW is a dead end (no back affordance shown for it, matches its own summary
    // screen having no back button) — only SIGN_IN/SIGN_UP have a back path.
    private fun onStepBack() {
        when (_uiState.value.accountSubStep) {
            AccountSubStep.SIGN_UP -> _uiState.update {
                it.copy(accountSubStep = AccountSubStep.SIGN_IN)
            }
            AccountSubStep.SIGN_IN -> eventChannel.trySend(
                OnboardingAccountSetupUiEvent.NavigateBack,
            )
            AccountSubStep.DATA_SYNC_REVIEW -> Unit
        }
    }

    private fun onSignInEmailChanged(value: String) {
        _uiState.update {
            it.copy(signInForm = it.signInForm.copy(email = value, emailError = false))
        }
    }

    private fun onSignInPasswordChanged(value: String) {
        _uiState.update {
            it.copy(signInForm = it.signInForm.copy(password = value, passwordError = false))
        }
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
        eventChannel.trySend(OnboardingAccountSetupUiEvent.ShowError(AuthError.Unknown.toUiState()))
    }

    private suspend fun applySignInResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(signInForm = SignInFormUiState()) }
            proceedAfterAuthSuccess(AllSetReason.SIGNED_IN)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = false)) }
            eventChannel.trySend(OnboardingAccountSetupUiEvent.ShowError(error.toUiState()))
        }
    }

    private fun onSignUpEmailChanged(value: String) {
        _uiState.update {
            it.copy(signUpForm = it.signUpForm.copy(email = value, emailError = false))
        }
    }

    private fun onSignUpPasswordChanged(value: String) {
        _uiState.update {
            it.copy(signUpForm = it.signUpForm.copy(password = value, passwordError = false))
        }
    }

    private fun onSignUpRepeatPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                signUpForm = it.signUpForm.copy(
                    repeatPassword = value,
                    repeatPasswordError = false,
                ),
            )
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

    private fun onSignUpGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = true)) }
            applySignUpResult(signInWithGoogle(idToken))
        }
    }

    private suspend fun applySignUpResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(signUpForm = SignUpFormUiState()) }
            proceedAfterAuthSuccess(AllSetReason.ACCOUNT_CREATED)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = false)) }
            eventChannel.trySend(OnboardingAccountSetupUiEvent.ShowError(error.toUiState()))
        }
    }

    // Shows the data-review substep only the first time there's unsynced local data to offer;
    // otherwise syncs transparently in the background and finishes immediately.
    private suspend fun proceedAfterAuthSuccess(reason: AllSetReason) {
        val summary = getLocalDataSummary()
        if (shouldReviewLocalDataBeforeSync(_uiState.value.hasSyncedLocalData, summary)) {
            pendingAllSetReason = reason
            _uiState.update {
                it.copy(
                    accountSubStep = AccountSubStep.DATA_SYNC_REVIEW,
                    dataSyncSummary = summary.toUiState(),
                )
            }
        } else {
            viewModelScope.launch { syncLocalData() }
            finish(reason)
        }
    }

    private fun onSyncConfirmClicked() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = syncLocalData()
            if (result.isSuccess) markLocalDataSynced()
            _uiState.update { it.copy(isSyncing = false, dataSyncSummary = null) }
            finish(pendingAllSetReason)
        }
    }

    private fun onSyncSkipClicked() {
        _uiState.update { it.copy(dataSyncSummary = null) }
        viewModelScope.launch { finish(pendingAllSetReason) }
    }

    private suspend fun finish(reason: AllSetReason) {
        val result = completeOnboarding()
        if (result.isSuccess) {
            eventChannel.trySend(OnboardingAccountSetupUiEvent.Finished(reason))
        }
    }
}
