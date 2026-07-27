package pl.luczka.todaywas.ui.onboarding

import pl.luczka.todaywas.domain.model.Focus

data class OnboardingUiState(
    val step: OnboardingStep,
    val selectedFocus: Focus?,
    val confirmedFocus: Focus?,
    val isSaving: Boolean,
    val saveError: Boolean,
)
