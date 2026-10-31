package pl.luczka.todaywas.ui.onboarding.allset

sealed interface OnboardingAllSetNavigation {

    data object Finished : OnboardingAllSetNavigation
}
