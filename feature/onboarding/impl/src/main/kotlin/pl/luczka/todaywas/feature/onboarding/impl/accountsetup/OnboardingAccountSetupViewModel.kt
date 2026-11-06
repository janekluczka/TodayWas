package pl.luczka.todaywas.feature.onboarding.impl.accountsetup

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
import pl.luczka.todaywas.core.domain.usecase.CompleteOnboardingUseCase
import pl.luczka.todaywas.core.domain.usecase.GetLocalDataSummaryUseCase
import pl.luczka.todaywas.core.domain.usecase.MarkLocalDataSyncedUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.core.domain.usecase.ShouldReviewLocalDataBeforeSyncUseCase
import pl.luczka.todaywas.core.domain.usecase.SyncLocalDataUseCase
import pl.luczka.todaywas.core.ui.auth.SignInFormUiState
import pl.luczka.todaywas.core.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.core.ui.auth.util.isValidEmail
import pl.luczka.todaywas.core.ui.auth.util.isValidPassword
import pl.luczka.todaywas.core.ui.auth.util.isValidRepeatPassword
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.core.ui.model.AuthErrorUiState
import pl.luczka.todaywas.core.ui.model.LocalDataSummaryUi
import pl.luczka.todaywas.domain.auth.model.AuthError
import pl.luczka.todaywas.domain.auth.model.AuthException
import pl.luczka.todaywas.domain.auth.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.auth.usecase.SignInWithEmailUseCase
import pl.luczka.todaywas.domain.auth.usecase.SignInWithGoogleUseCase
import pl.luczka.todaywas.domain.auth.usecase.SignUpWithEmailUseCase
import pl.luczka.todaywas.feature.onboarding.api.navigation.AllSetReason
import pl.luczka.todaywas.feature.onboarding.impl.AccountSubStep

@HiltViewModel
internal class OnboardingAccountSetupViewModel @Inject constructor(
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

    // State owned by this screen itself, as opposed to the auth and onboarding state it observes.
    private data class ScreenState(
        val accountSubStep: AccountSubStep = AccountSubStep.SIGN_IN,
        val signInForm: SignInFormUiState = SignInFormUiState(),
        val signUpForm: SignUpFormUiState = SignUpFormUiState(),
        val dataSyncSummary: LocalDataSummaryUi? = null,
        val isSyncing: Boolean = false,
        val pendingNavigation: OnboardingAccountSetupNavigation? = null,
        val userMessage: AuthErrorUiState? = null,
    )

    private val screenState = MutableStateFlow(ScreenState())

    val uiState: StateFlow<OnboardingAccountSetupUiState> = combine(
        observeAuthState(),
        observeOnboardingState(),
        screenState,
    ) { authState, onboardingState, screen ->
        OnboardingAccountSetupUiState(
            accountSubStep = screen.accountSubStep,
            authState = authState.toUiState(),
            signInForm = screen.signInForm,
            signUpForm = screen.signUpForm,
            hasSyncedLocalData = onboardingState.hasSyncedLocalData,
            dataSyncSummary = screen.dataSyncSummary,
            isSyncing = screen.isSyncing,
            pendingNavigation = screen.pendingNavigation,
            userMessage = screen.userMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingAccountSetupUiState(),
    )

    private var pendingAllSetReason = AllSetReason.NO_ACCOUNT

    fun onIntent(intent: OnboardingAccountSetupIntent) {
        when (intent) {
            OnboardingAccountSetupIntent.StepBack -> onStepBack()
            OnboardingAccountSetupIntent.NavigationHandled -> onNavigationHandled()
            OnboardingAccountSetupIntent.UserMessageShown -> onUserMessageShown()
            is OnboardingAccountSetupIntent.SignInEmailChanged -> onSignInEmailChanged(intent.value)
            is OnboardingAccountSetupIntent.SignInPasswordChanged -> onSignInPasswordChanged(intent.value)
            OnboardingAccountSetupIntent.SignInSubmitClicked -> onSignInSubmitClicked()
            is OnboardingAccountSetupIntent.SignInGoogleIdTokenReceived -> onSignInGoogleIdTokenReceived(intent.idToken)
            is OnboardingAccountSetupIntent.GoogleSignInFailed -> onGoogleSignInFailed(intent.error)
            OnboardingAccountSetupIntent.SignUpLinkClicked -> onSignUpLinkClicked()
            OnboardingAccountSetupIntent.SignInLinkClicked -> onSignInLinkClicked()
            is OnboardingAccountSetupIntent.SignUpEmailChanged -> onSignUpEmailChanged(intent.value)
            is OnboardingAccountSetupIntent.SignUpPasswordChanged -> onSignUpPasswordChanged(intent.value)
            is OnboardingAccountSetupIntent.SignUpRepeatPasswordChanged -> onSignUpRepeatPasswordChanged(intent.value)
            OnboardingAccountSetupIntent.SignUpSubmitClicked -> onSignUpSubmitClicked()
            is OnboardingAccountSetupIntent.SignUpGoogleIdTokenReceived -> onSignUpGoogleIdTokenReceived(intent.idToken)
            OnboardingAccountSetupIntent.SyncConfirmClicked -> onSyncConfirmClicked()
            OnboardingAccountSetupIntent.SyncSkipClicked -> onSyncSkipClicked()
        }
    }

    // DATA_SYNC_REVIEW is a dead end (no back affordance shown for it, matches its own summary
    // screen having no back button) — only SIGN_IN/SIGN_UP have a back path.
    private fun onStepBack() {
        when (screenState.value.accountSubStep) {
            AccountSubStep.SIGN_UP -> screenState.update { it.copy(accountSubStep = AccountSubStep.SIGN_IN) }
            AccountSubStep.SIGN_IN -> screenState.update {
                it.copy(pendingNavigation = OnboardingAccountSetupNavigation.Back)
            }
            AccountSubStep.DATA_SYNC_REVIEW -> Unit
        }
    }

    private fun onNavigationHandled() {
        screenState.update { it.copy(pendingNavigation = null) }
    }

    private fun onUserMessageShown() {
        screenState.update { it.copy(userMessage = null) }
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
            proceedAfterAuthSuccess(AllSetReason.SIGNED_IN)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            screenState.update { it.copy(signInForm = it.signInForm.copy(isSubmitting = false)) }
            screenState.update { it.copy(userMessage = error.toUiState()) }
        }
    }

    private fun onSignUpLinkClicked() {
        screenState.update { it.copy(accountSubStep = AccountSubStep.SIGN_UP) }
    }

    private fun onSignInLinkClicked() {
        screenState.update { it.copy(accountSubStep = AccountSubStep.SIGN_IN) }
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
            proceedAfterAuthSuccess(AllSetReason.ACCOUNT_CREATED)
        } else {
            val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
            screenState.update { it.copy(signUpForm = it.signUpForm.copy(isSubmitting = false)) }
            screenState.update { it.copy(userMessage = error.toUiState()) }
        }
    }

    // Shows the data-review substep only the first time there's unsynced local data to offer;
    // otherwise syncs transparently in the background and finishes immediately.
    private suspend fun proceedAfterAuthSuccess(reason: AllSetReason) {
        val summary = getLocalDataSummary()
        if (shouldReviewLocalDataBeforeSync(uiState.value.hasSyncedLocalData, summary)) {
            pendingAllSetReason = reason
            screenState.update {
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
        if (screenState.value.isSyncing) return
        viewModelScope.launch {
            screenState.update { it.copy(isSyncing = true) }
            val result = syncLocalData()
            screenState.update { it.copy(isSyncing = false) }
            if (result.isSuccess) {
                markLocalDataSynced()
                screenState.update { it.copy(dataSyncSummary = null) }
                finish(pendingAllSetReason)
            } else {
                val error = (result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown
                screenState.update { it.copy(userMessage = error.toUiState()) }
            }
        }
    }

    private fun onSyncSkipClicked() {
        screenState.update { it.copy(dataSyncSummary = null) }
        viewModelScope.launch { finish(pendingAllSetReason) }
    }

    private suspend fun finish(reason: AllSetReason) {
        val result = completeOnboarding()
        if (result.isSuccess) {
            screenState.update { it.copy(pendingNavigation = OnboardingAccountSetupNavigation.Finished(reason)) }
        }
    }
}
