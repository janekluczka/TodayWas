package pl.luczka.todaywas.ui.onboarding.accountsetup

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.LocalDataSummaryUi
import pl.luczka.todaywas.ui.onboarding.AccountSubStep

@Immutable
data class OnboardingAccountSetupUiState(
    val accountSubStep: AccountSubStep = AccountSubStep.SIGN_IN,
    val authState: AuthStateUi = AuthStateUi.Loading,
    val signInForm: SignInFormUiState = SignInFormUiState(),
    val signUpForm: SignUpFormUiState = SignUpFormUiState(),
    val hasSyncedLocalData: Boolean = false,
    val dataSyncSummary: LocalDataSummaryUi? = null,
    val isSyncing: Boolean = false,
)
