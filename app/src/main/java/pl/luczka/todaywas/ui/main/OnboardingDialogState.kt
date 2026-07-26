package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.domain.model.Focus

data class OnboardingDialogState(
    val onboardingMode: OnboardingMode,
    val onboardingStep: OnboardingStep,
    val selectedFocusInDialog: Focus?,
    val isSaving: Boolean,
    val saveError: Boolean,
)
