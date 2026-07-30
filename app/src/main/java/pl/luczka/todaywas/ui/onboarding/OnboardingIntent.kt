package pl.luczka.todaywas.ui.onboarding

import pl.luczka.todaywas.ui.model.FocusUiState

sealed interface OnboardingIntent {

    data object NextClicked : OnboardingIntent

    data object SkipClicked : OnboardingIntent

    data object StepBack : OnboardingIntent

    data class FocusOptionSelected(
        val focus: FocusUiState,
    ) : OnboardingIntent

    data object CreateAccountClicked : OnboardingIntent
}
