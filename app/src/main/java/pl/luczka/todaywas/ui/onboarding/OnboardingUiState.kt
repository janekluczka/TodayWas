package pl.luczka.todaywas.ui.onboarding

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.auth.AuthFormUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.FocusUiState

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep,
    val selectedFocus: FocusUiState?,
    val confirmedFocus: FocusUiState?,
    val isSaving: Boolean,
    val saveError: Boolean,
    val accountSubStep: AccountSubStep,
    val authState: AuthStateUi,
    val authForm: AuthFormUiState,
)
