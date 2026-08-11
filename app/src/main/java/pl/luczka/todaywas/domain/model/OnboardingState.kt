package pl.luczka.todaywas.domain.model

data class OnboardingState(
    val completed: Boolean,
    val focus: Focus?,
    val hasSyncedLocalData: Boolean,
)
