package pl.luczka.todaywas.feature.onboarding.impl.allset

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.feature.onboarding.api.navigation.AllSetReason

@Immutable
internal data class OnboardingAllSetUiState(
    val reason: AllSetReason,
    val email: String? = null,
    val pendingNavigation: OnboardingAllSetNavigation? = null,
)
