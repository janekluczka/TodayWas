package pl.luczka.todaywas.feature.onboarding.impl.accountsetup

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.core.ui.auth.SignInFormUiState
import pl.luczka.todaywas.core.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.core.ui.model.AuthErrorUiState
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.core.ui.model.LocalDataSummaryUi
import pl.luczka.todaywas.feature.onboarding.impl.AccountSubStep

@Immutable
internal data class OnboardingAccountSetupUiState(
    val accountSubStep: AccountSubStep = AccountSubStep.SIGN_IN,
    val authState: AuthStateUi = AuthStateUi.Loading,
    val signInForm: SignInFormUiState = SignInFormUiState(),
    val signUpForm: SignUpFormUiState = SignUpFormUiState(),
    val hasSyncedLocalData: Boolean = false,
    val dataSyncSummary: LocalDataSummaryUi? = null,
    val isSyncing: Boolean = false,
    val pendingNavigation: OnboardingAccountSetupNavigation? = null,
    // An error to show once in a snackbar; cleared by UserMessageShown.
    val userMessage: AuthErrorUiState? = null,
)
