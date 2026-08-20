package pl.luczka.todaywas.domain.model

data class ContributionSummary(
    val grid: ContributionGrid,
    val availableWindows: List<ContributionWindow>,
)
