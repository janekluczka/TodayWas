package pl.luczka.todaywas.ui.onboarding.allset

sealed interface OnboardingAllSetUiEvent {

    data object Finished : OnboardingAllSetUiEvent
}
