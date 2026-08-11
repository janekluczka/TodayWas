package pl.luczka.todaywas.ui.onboarding

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.auth.SignInFormUiState
import pl.luczka.todaywas.ui.auth.SignUpFormUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.LocalDataSummaryUi

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep,
    val selectedFocus: FocusUiState?,
    val confirmedFocus: FocusUiState?,
    val isSaving: Boolean,
    val saveError: Boolean,
    val accountSubStep: AccountSubStep,
    val authState: AuthStateUi,
    val signInForm: SignInFormUiState,
    val signUpForm: SignUpFormUiState,
    val allSetReason: AllSetReason,
    val hasSyncedLocalData: Boolean = false,
    val dataSyncSummary: LocalDataSummaryUi? = null,
    val isSyncing: Boolean = false,
)
