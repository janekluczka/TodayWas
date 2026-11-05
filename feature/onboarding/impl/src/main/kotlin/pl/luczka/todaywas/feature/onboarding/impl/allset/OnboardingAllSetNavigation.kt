package pl.luczka.todaywas.feature.onboarding.impl.allset

sealed interface OnboardingAllSetNavigation {

    data object Finished : OnboardingAllSetNavigation
}
