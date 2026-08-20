package pl.luczka.todaywas.ui.onboarding.allset

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.onboarding.AllSetReason

@Immutable
data class OnboardingAllSetUiState(
    val reason: AllSetReason,
    val email: String? = null,
)
