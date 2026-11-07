package pl.luczka.todaywas.domain.onboarding.model

data class OnboardingState(
    val completed: Boolean,
    val hasSyncedLocalData: Boolean,
)
