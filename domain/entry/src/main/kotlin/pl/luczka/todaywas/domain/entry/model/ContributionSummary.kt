package pl.luczka.todaywas.domain.entry.model

data class ContributionSummary(
    val grid: ContributionGrid,
    val availableWindows: List<ContributionWindow>,
)
