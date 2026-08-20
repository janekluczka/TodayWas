package pl.luczka.todaywas.ui.onboarding.choice

import androidx.compose.runtime.Immutable

@Immutable
data class OnboardingChoiceUiState(
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
)
