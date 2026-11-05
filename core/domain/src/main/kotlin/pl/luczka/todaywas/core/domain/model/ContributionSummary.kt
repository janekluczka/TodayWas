package pl.luczka.todaywas.core.domain.model

data class ContributionSummary(
    val grid: ContributionGrid,
    val availableWindows: List<ContributionWindow>,
)
