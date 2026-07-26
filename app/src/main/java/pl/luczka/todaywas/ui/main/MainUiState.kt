package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.domain.model.Focus

data class MainUiState(
    val currentFocus: Focus?,
    val showOnboardingDialog: Boolean,
    val onboardingMode: OnboardingMode,
    val onboardingStep: OnboardingStep,
    val selectedFocusInDialog: Focus?,
    val isSaving: Boolean,
    val saveError: Boolean,
)
