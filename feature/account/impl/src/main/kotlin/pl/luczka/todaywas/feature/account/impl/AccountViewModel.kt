package pl.luczka.todaywas.feature.account.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.core.domain.model.AuthError
import pl.luczka.todaywas.core.domain.model.AuthException
import pl.luczka.todaywas.core.domain.usecase.GetLocalDataSummaryUseCase
import pl.luczka.todaywas.core.domain.usecase.MarkLocalDataSyncedUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.core.domain.usecase.ShouldReviewLocalDataBeforeSyncUseCase
import pl.luczka.todaywas.core.domain.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.core.domain.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.core.domain.usecase.SignOutUseCase
import pl.luczka.todaywas.core.domain.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.core.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.core.ui.auth.SignInFormUiState
import pl.luczka.todaywas.core.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.core.ui.auth.util.isValidEmail
import pl.luczka.todaywas.core.ui.auth.util.isValidPassword
import pl.luczka.todaywas.core.ui.auth.util.isValidRepeatPassword
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.core.ui.model.AuthErrorUiState
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.core.ui.model.LocalDataSummaryUi

@HiltViewModel
internal class AccountViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    observeOnboardingState: ObserveOnboardingStateUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signOut: SignOutUseCase,
    private val getLocalDataSummary: GetLocalDataSummaryUseCase,
    private val syncLocalData: SyncLocalDataUseCase,
    private val markLocalDataSynced: MarkLocalDataSyncedUseCase,
    private val shouldReviewLocalDataBeforeSync: ShouldReviewLocalDataBeforeSyncUseCase,
) : ViewModel() {

    private enum class PostSyncAction { NAVIGATE_BACK, SHOW_SUCCESS, RETURN_HOME }

    // State owned by this screen itself, as opposed to the auth and onboarding state it observes.
    private data class ScreenState(
        val step: AccountStep = AccountStep.SIGN_IN,
        val signInForm: SignInFormUiState = SignInFormUiState(),
        val signUpForm: SignUpFormUiState = SignUpFormUiState(),
        val isSigningOut: Boolean = false,
        val dataSyncSummary: LocalDataSummaryUi? = null,
        val isSyncing: Boolean = false,
        val pendingNavigation: AccountNavigation? = null,
        val userMessage: AuthErrorUiState? = null,
    )

    private val screenState = MutableStateFlow(ScreenState())

    val uiState: StateFlow<AccountUiState> = combine(
        observeAuthState(),
        observeOnboardingState(),
        screenState,
    ) { authState, onboardingState, screen ->
        AccountUiState(
            authState = authState.toUiState(),
            step = screen.step,
            signInForm = screen.signInForm,
            signUpForm = screen.signUpForm,
            isSigningOut = screen.isSigningOut,
            dataSyncSummary = screen.dataSyncSummary,
            isSyncing = screen.isSyncing,
            hasSyncedLocalData = onboardingState.hasSyncedLocalData,
            pendingNavigation = screen.pendingNavigation,
            userMessage = screen.userMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState(
            authState = AuthStateUi.Loading,
            step = AccountStep.SIGN_IN,
            signInForm = SignInFormUiState(),
            signUpForm = SignUpFormUiState(),
        ),
    )

    private var postSyncAction = PostSyncAction.RETURN_HOME

    fun onIntent(intent: AccountIntent) {
        when (intent) {
            AccountIntent.BackClicked -> onBackClicked()
            is AccountIntent.SignInEmailChanged -> onSignInEmailChanged(intent.value)
            is AccountIntent.SignInPasswordChanged -> onSignInPasswordChanged(intent.value)
            AccountIntent.SignInSubmitClicked -> onSignInSubmitClicked()
            is AccountIntent.SignInGoogleIdTokenReceived -> onSignInGoogleIdTokenReceived(intent.idToken)
            is AccountIntent.GoogleSignInFailed -> onGoogleSignInFailed(intent.error)
            AccountIntent.SignUpLinkClicked -> onSignUpLinkClicked()
            AccountIntent.SignInLinkClicked -> onSignInLinkClicked()
            is AccountIntent.SignUpEmailChanged -> onSignUpEmailChanged(intent.value)
            is AccountIntent.SignUpPasswordChanged -> onSignUpPasswordChanged(intent.value)
            is AccountIntent.SignUpRepeatPasswordChanged -> onSignUpRepeatPasswordChanged(intent.value)
            AccountIntent.SignUpSubmitClicked -> onSignUpSubmitClicked()
            is AccountIntent.SignUpGoogleIdTokenReceived -> onSignUpGoogleIdTokenReceived(intent.idToken)
            AccountIntent.ContinueClicked -> onContinueClicked()
            AccountIntent.SignOutClicked -> onSignOutClicked()
            AccountIntent.SyncConfirmClicked -> onSyncConfirmClicked()
            AccountIntent.SyncSkipClicked -> onSyncSkipClicked()
            AccountIntent.SyncLocalDataClicked -> onSyncLocalDataClicked()
            AccountIntent.NavigationHandled -> onNavigationHandled()
            AccountIntent.UserMessageShown -> onUserMessageShown()
        }
    }

    private fun onBackClicked() {
        if (screenState.value.step == AccountStep.SIGN_UP) {
            screenState.update { it.copy(step = AccountStep.SIGN_IN) }
        } else {
            screenState.update { it.copy(pendingNavigation = AccountNavigation.Back) }
        }
    }

    private fun onSignInEmailChanged(value: String) {
        screenState.update {
            it.copy(
                signInForm = it.signInForm.copy(
                    email = value,
                    emailError = false,
                ),
            )
        }
    }

    private fun onSignInPasswordChanged(value: String) {
        screenState.update {
            it.copy(
                signInForm = it.signInForm.copy(
                    password = value,
                    passwordError = false,
                ),
            )
        }
    }

    private fun onSignInSubmitClicked() {
        val form = screenState.value.signInForm
        if (form.isSubmitting) return

        val emailValid = isValidEmail(form.email)
        val passwordValid = isValidPassword(form.password)
        if (!emailValid || !passwordValid) {
            screenState.update {
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
            screenState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = true)) }
            applySignInResult(signInWithEmail(form.email, form.password))
        }
    }

    private fun onSignInGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            screenState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = true)) }
            applySignInResult(signInWithGoogle(idToken))
        }
    }

    private fun onGoogleSignInFailed(error: AuthErrorUiState) {
        screenState.update { it.copy(userMessage = error) }
    }

    private suspend fun applySignInResult(result: Result<Unit>) {
        if (result.isSuccess) {
            screenState.update { it.copy(signInForm = SignInFormUiState()) }
            proceedAfterAuthSuccess(PostSyncAction.NAVIGATE_BACK)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            screenState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = false)) }
            screenState.update { it.copy(userMessage = error.toUiState()) }
        }
    }

    private fun onSignUpLinkClicked() {
        screenState.update { it.copy(step = AccountStep.SIGN_UP) }
    }

    private fun onSignInLinkClicked() {
        screenState.update { it.copy(step = AccountStep.SIGN_IN) }
    }

    private fun onSignUpEmailChanged(value: String) {
        screenState.update {
            it.copy(
                signUpForm = it.signUpForm.copy(
                    email = value,
                    emailError = false,
                ),
            )
        }
    }

    private fun onSignUpPasswordChanged(value: String) {
        screenState.update {
            it.copy(
                signUpForm = it.signUpForm.copy(
                    password = value,
                    passwordError = false,
                ),
            )
        }
    }

    private fun onSignUpRepeatPasswordChanged(value: String) {
        screenState.update {
            it.copy(
                signUpForm = it.signUpForm.copy(
                    repeatPassword = value,
                    repeatPasswordError = false,
                ),
            )
        }
    }

    private fun onSignUpSubmitClicked() {
        val form = screenState.value.signUpForm
        if (form.isSubmitting) return

        val emailValid = isValidEmail(form.email)
        val passwordValid = isValidPassword(form.password)
        val repeatPasswordValid = isValidRepeatPassword(form.password, form.repeatPassword)
        if (!emailValid || !passwordValid || !repeatPasswordValid) {
            screenState.update {
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
            screenState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = true)) }
            val result = signUpWithEmail(form.email, form.password)
            applySignUpResult(result)
        }
    }

    private fun onSignUpGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            screenState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = true)) }
            applySignUpResult(signInWithGoogle(idToken))
        }
    }

    private suspend fun applySignUpResult(result: Result<Unit>) {
        if (result.isSuccess) {
            screenState.update { it.copy(signUpForm = SignUpFormUiState()) }
            proceedAfterAuthSuccess(PostSyncAction.SHOW_SUCCESS)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            screenState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = false)) }
            screenState.update { it.copy(userMessage = error.toUiState()) }
        }
    }

    // Shows the data-review step only the first time there's unsynced local data to offer;
    // otherwise syncs transparently in the background and proceeds as before.
    private suspend fun proceedAfterAuthSuccess(whenDone: PostSyncAction) {
        val summary = getLocalDataSummary()
        if (shouldReviewLocalDataBeforeSync(uiState.value.hasSyncedLocalData, summary)) {
            postSyncAction = whenDone
            screenState.update {
                it.copy(
                    step = AccountStep.DATA_SYNC_REVIEW,
                    dataSyncSummary = summary.toUiState(),
                )
            }
        } else {
            viewModelScope.launch { syncLocalData() }
            finishPostSyncAction(whenDone)
        }
    }

    private fun onContinueClicked() {
        screenState.update { it.copy(pendingNavigation = AccountNavigation.Back) }
    }

    private fun onSyncConfirmClicked() {
        if (screenState.value.isSyncing) return
        viewModelScope.launch {
            screenState.update { it.copy(isSyncing = true) }
            val result = syncLocalData()
            screenState.update { it.copy(isSyncing = false) }
            if (result.isSuccess) {
                markLocalDataSynced()
                screenState.update { it.copy(dataSyncSummary = null) }
                finishPostSyncAction(postSyncAction)
            } else {
                val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
                screenState.update { it.copy(userMessage = error.toUiState()) }
            }
        }
    }

    private fun onSyncSkipClicked() {
        screenState.update { it.copy(dataSyncSummary = null) }
        finishPostSyncAction(postSyncAction)
    }

    private fun onSyncLocalDataClicked() {
        viewModelScope.launch {
            val summary = getLocalDataSummary()
            if (!summary.isEmpty) {
                postSyncAction = PostSyncAction.RETURN_HOME
                screenState.update {
                    it.copy(
                        step = AccountStep.DATA_SYNC_REVIEW,
                        dataSyncSummary = summary.toUiState(),
                    )
                }
            }
        }
    }

    private fun finishPostSyncAction(action: PostSyncAction) {
        when (action) {
            PostSyncAction.NAVIGATE_BACK -> screenState.update { it.copy(pendingNavigation = AccountNavigation.Back) }
            PostSyncAction.SHOW_SUCCESS -> screenState.update { it.copy(step = AccountStep.SUCCESS) }
            PostSyncAction.RETURN_HOME -> screenState.update { it.copy(step = AccountStep.SIGN_IN) }
        }
    }

    private fun onSignOutClicked() {
        if (screenState.value.isSigningOut) return
        viewModelScope.launch {
            screenState.update { it.copy(isSigningOut = true) }
            val result = signOut()
            if (result.isSuccess) {
                screenState.update {
                    it.copy(
                        step = AccountStep.SIGN_IN,
                        isSigningOut = false,
                    )
                }
            } else {
                val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
                screenState.update { it.copy(isSigningOut = false) }
                screenState.update { it.copy(userMessage = error.toUiState()) }
            }
        }
    }

    private fun onNavigationHandled() {
        screenState.update { it.copy(pendingNavigation = null) }
    }

    private fun onUserMessageShown() {
        screenState.update { it.copy(userMessage = null) }
    }
}
