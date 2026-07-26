package pl.luczka.todaywas.ui.main

import pl.luczka.todaywas.domain.model.Focus

data class MainUiState(
    val currentFocus: Focus?,
    val showOnboardingDialog: Boolean,
    val onboardingDialogState: OnboardingDialogState,
)
