package pl.luczka.todaywas.ui.onboarding

sealed interface OnboardingUiEvent {

    data object ExitApp : OnboardingUiEvent

    data object Finished : OnboardingUiEvent
}
