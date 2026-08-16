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
import pl.luczka.todaywas.domain.usecase.GetLocalDataSummaryUseCase
import pl.luczka.todaywas.domain.usecase.MarkLocalDataSyncedUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.auth.util.isValidEmail
import pl.luczka.todaywas.ui.auth.util.isValidPassword
import pl.luczka.todaywas.ui.auth.util.isValidRepeatPassword
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.toUiState
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    observeOnboardingState: ObserveOnboardingStateUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signOut: SignOutUseCase,
    private val getLocalDataSummary: GetLocalDataSummaryUseCase,
    private val syncLocalData: SyncLocalDataUseCase,
    private val markLocalDataSynced: MarkLocalDataSyncedUseCase,
) : ViewModel() {

    private enum class PostSyncAction { NAVIGATE_BACK, SHOW_SUCCESS, RETURN_HOME }

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

    private var postSyncAction = PostSyncAction.RETURN_HOME

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
            AccountIntent.SyncConfirmClicked -> onSyncConfirmClicked()
            AccountIntent.SyncSkipClicked -> onSyncSkipClicked()
            AccountIntent.SyncLocalDataClicked -> onSyncLocalDataClicked()
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

    private suspend fun applySignInResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(signInForm = SignInFormUiState()) }
            proceedAfterAuthSuccess(PostSyncAction.NAVIGATE_BACK)
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

    private suspend fun applySignUpResult(result: Result<Unit>) {
        if (result.isSuccess) {
            _uiState.update { it.copy(signUpForm = SignUpFormUiState()) }
            proceedAfterAuthSuccess(PostSyncAction.SHOW_SUCCESS)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            _uiState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = false)) }
            eventChannel.trySend(AccountUiEvent.ShowError(error.toUiState()))
        }
    }

    // Shows the data-review step only the first time there's unsynced local data to offer;
    // otherwise syncs transparently in the background and proceeds as before.
    private suspend fun proceedAfterAuthSuccess(whenDone: PostSyncAction) {
        val summary = getLocalDataSummary()
        if (!_uiState.value.hasSyncedLocalData && !summary.isEmpty) {
            postSyncAction = whenDone
            _uiState.update {
                it.copy(step = AccountStep.DATA_SYNC_REVIEW, dataSyncSummary = summary.toUiState())
            }
        } else {
            viewModelScope.launch { syncLocalData() }
            finishPostSyncAction(whenDone)
        }
    }

    private fun onSyncConfirmClicked() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = syncLocalData()
            if (result.isSuccess) markLocalDataSynced()
            _uiState.update { it.copy(isSyncing = false, dataSyncSummary = null) }
            finishPostSyncAction(postSyncAction)
        }
    }

    private fun onSyncSkipClicked() {
        _uiState.update { it.copy(dataSyncSummary = null) }
        finishPostSyncAction(postSyncAction)
    }

    private fun onSyncLocalDataClicked() {
        viewModelScope.launch {
            val summary = getLocalDataSummary()
            if (!summary.isEmpty) {
                postSyncAction = PostSyncAction.RETURN_HOME
                _uiState.update {
                    it.copy(step = AccountStep.DATA_SYNC_REVIEW, dataSyncSummary = summary.toUiState())
                }
            }
        }
    }

    private fun finishPostSyncAction(action: PostSyncAction) {
        when (action) {
            PostSyncAction.NAVIGATE_BACK -> eventChannel.trySend(AccountUiEvent.NavigatedBack)
            PostSyncAction.SHOW_SUCCESS -> _uiState.update { it.copy(step = AccountStep.SUCCESS) }
            PostSyncAction.RETURN_HOME -> _uiState.update { it.copy(step = AccountStep.SIGN_IN) }
        }
    }

    private fun onSignOutClicked() {
        if (_uiState.value.isSigningOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            val result = signOut()
            if (result.isSuccess) {
                _uiState.update { it.copy(step = AccountStep.SIGN_IN, isSigningOut = false) }
            } else {
                val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
                _uiState.update { it.copy(isSigningOut = false) }
                eventChannel.trySend(AccountUiEvent.ShowError(error.toUiState()))
            }
        }
    }
}
