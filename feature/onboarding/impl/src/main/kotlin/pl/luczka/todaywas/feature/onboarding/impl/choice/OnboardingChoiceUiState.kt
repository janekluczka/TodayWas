package pl.luczka.todaywas.feature.onboarding.impl.choice

import androidx.compose.runtime.Immutable

@Immutable
internal data class OnboardingChoiceUiState(
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
    val pendingNavigation: OnboardingChoiceNavigation? = null,
)
