package pl.luczka.todaywas.core.domain.model

data class OnboardingState(
    val completed: Boolean,
    val hasSyncedLocalData: Boolean,
)
