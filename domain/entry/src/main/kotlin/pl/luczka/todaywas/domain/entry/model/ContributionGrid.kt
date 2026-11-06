package pl.luczka.todaywas.domain.entry.model

import java.time.LocalDate

data class ContributionGrid(
    val window: ContributionWindow,
    val days: Map<LocalDate, ContributionLevel>,
)
