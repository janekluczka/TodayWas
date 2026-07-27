package pl.luczka.todaywas.ui.onboarding

import pl.luczka.todaywas.domain.model.Focus

sealed interface OnboardingIntent {

    data object NextClicked : OnboardingIntent

    data object SkipClicked : OnboardingIntent

    data object StepBack : OnboardingIntent

    data class FocusOptionSelected(
        val focus: Focus,
    ) : OnboardingIntent

    data object CreateAccountClicked : OnboardingIntent
}
