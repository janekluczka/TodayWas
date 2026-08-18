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
import pl.luczka.todaywas.domain.usecase.CompleteOnboardingUseCase
import pl.luczka.todaywas.domain.usecase.GetLocalDataSummaryUseCase
import pl.luczka.todaywas.domain.usecase.MarkLocalDataSyncedUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.domain.util.LocalDataSyncPolicy
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.auth.util.isValidEmail
import pl.luczka.todaywas.ui.auth.util.isValidPassword
import pl.luczka.todaywas.ui.auth.util.isValidRepeatPassword
import pl.luczka.todaywas.ui.mapper.toUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    observeOnboardingState: ObserveOnboardingStateUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val getLocalDataSummary: GetLocalDataSummaryUseCase,
    private val syncLocalData: SyncLocalDataUseCase,
    private val markLocalDataSynced: MarkLocalDataSyncedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = OnboardingStep.WELCOME,
            isSaving = false,
            saveError = false,
            accountSubStep = AccountSubStep.CHOICE,
            authState = AuthStateUi.Loading,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
            allSetReason = AllSetReason.NO_ACCOUNT,
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<OnboardingUiEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingUiEvent> = eventChannel.receiveAsFlow()

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

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.NextClicked -> onNextClicked()
            OnboardingIntent.SkipClicked -> onSkipClicked()
            OnboardingIntent.StepBack -> onStepBack()
            OnboardingIntent.ContinueWithoutAccountClicked -> onContinueWithoutAccountClicked()
            OnboardingIntent.SignInSignUpClicked ->
                _uiState.update { it.copy(accountSubStep = AccountSubStep.SIGN_IN) }
            is OnboardingIntent.SignInEmailChanged -> onSignInEmailChanged(intent.value)
            is OnboardingIntent.SignInPasswordChanged -> onSignInPasswordChanged(intent.value)
            OnboardingIntent.SignInSubmitClicked -> onSignInSubmitClicked()
            is OnboardingIntent.SignInGoogleIdTokenReceived -> onSignInGoogleIdTokenReceived(intent.idToken)
            OnboardingIntent.GoogleSignInFailed -> onGoogleSignInFailed()
            OnboardingIntent.SignUpLinkClicked ->
                _uiState.update { it.copy(accountSubStep = AccountSubStep.SIGN_UP) }
            is OnboardingIntent.SignUpEmailChanged -> onSignUpEmailChanged(intent.value)
            is OnboardingIntent.SignUpPasswordChanged -> onSignUpPasswordChanged(intent.value)
            is OnboardingIntent.SignUpRepeatPasswordChanged -> onSignUpRepeatPasswordChanged(intent.value)
            OnboardingIntent.SignUpSubmitClicked -> onSignUpSubmitClicked()
            OnboardingIntent.SyncConfirmClicked -> onSyncConfirmClicked()
            OnboardingIntent.SyncSkipClicked -> onSyncSkipClicked()
        }
    }

    private fun onNextClicked() {
        when (_uiState.value.step) {
            OnboardingStep.WELCOME -> _uiState.update { it.copy(step = OnboardingStep.ACCOUNT_INFO) }
            OnboardingStep.ACCOUNT_INFO -> onCompleteAccountStep()
            OnboardingStep.ALL_SET -> eventChannel.trySend(OnboardingUiEvent.Finished)
        }
    }

    private fun onCompleteAccountStep() {
        if (_uiState.value.isSaving) return
        val reason = if (_uiState.value.authState is AuthStateUi.SignedIn) {
            AllSetReason.SIGNED_IN
        } else {
            AllSetReason.NO_ACCOUNT
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = reachAllSet(reason)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveError = result.isFailure,
                )
            }
        }
    }

    // The single place onboarding is marked complete for every path that actually visits the
    // ALL_SET step. SkipClicked bypasses ALL_SET entirely (it finishes straight from wherever the
    // user is) and persists via completeOnboarding() directly instead. Both persist before
    // advancing so RootViewModel's routing decision on a later cold start never races an
    // unpersisted completion.
    private suspend fun reachAllSet(reason: AllSetReason): Result<Unit> {
        val result = completeOnboarding()
        if (result.isSuccess) {
            _uiState.update { it.copy(step = OnboardingStep.ALL_SET, allSetReason = reason) }
        }
        return result
    }

    private fun onStepBack() {
        when (_uiState.value.step) {
            OnboardingStep.ACCOUNT_INFO -> onAccountInfoStepBack()
            OnboardingStep.ALL_SET -> _uiState.update { it.copy(step = OnboardingStep.ACCOUNT_INFO) }
            OnboardingStep.WELCOME -> eventChannel.trySend(OnboardingUiEvent.ExitApp)
        }
    }

    private fun onAccountInfoStepBack() {
        when (_uiState.value.accountSubStep) {
            AccountSubStep.SIGN_UP -> _uiState.update { it.copy(accountSubStep = AccountSubStep.SIGN_IN) }
            AccountSubStep.SIGN_IN -> _uiState.update { it.copy(accountSubStep = AccountSubStep.CHOICE) }
            AccountSubStep.DATA_SYNC_REVIEW -> Unit
            AccountSubStep.CHOICE -> _uiState.update { it.copy(step = OnboardingStep.WELCOME) }
        }
    }

    private fun onSkipClicked() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveError = false,
                )
            }
            val result = completeOnboarding()
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

    private fun onContinueWithoutAccountClicked() {
        viewModelScope.launch { reachAllSet(AllSetReason.NO_ACCOUNT) }
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
        eventChannel.trySend(OnboardingUiEvent.ShowError(AuthError.Unknown.toUiState()))
    }

    private suspend fun applySignInResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(signInForm = SignInFormUiState()) }
            proceedAfterAuthSuccess(AllSetReason.SIGNED_IN)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = false)) }
            eventChannel.trySend(OnboardingUiEvent.ShowError(error.toUiState()))
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

    private suspend fun applySignUpResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(signUpForm = SignUpFormUiState()) }
            proceedAfterAuthSuccess(AllSetReason.ACCOUNT_CREATED)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = false)) }
            eventChannel.trySend(OnboardingUiEvent.ShowError(error.toUiState()))
        }
    }

    // Shows the data-review step only the first time there's unsynced local data to offer;
    // otherwise syncs transparently in the background and proceeds straight to ALL_SET.
    private suspend fun proceedAfterAuthSuccess(reason: AllSetReason) {
        val summary = getLocalDataSummary()
        if (LocalDataSyncPolicy.shouldReviewBeforeSync(_uiState.value.hasSyncedLocalData, summary)) {
            pendingAllSetReason = reason
            _uiState.update {
                it.copy(
                    accountSubStep = AccountSubStep.DATA_SYNC_REVIEW,
                    dataSyncSummary = summary.toUiState(),
                )
            }
        } else {
            viewModelScope.launch { syncLocalData() }
            reachAllSet(reason)
        }
    }

    private fun onSyncConfirmClicked() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = syncLocalData()
            if (result.isSuccess) markLocalDataSynced()
            reachAllSet(pendingAllSetReason)
            _uiState.update { it.copy(isSyncing = false, dataSyncSummary = null) }
        }
    }

    private fun onSyncSkipClicked() {
        viewModelScope.launch {
            reachAllSet(pendingAllSetReason)
            _uiState.update { it.copy(dataSyncSummary = null) }
        }
    }
}
