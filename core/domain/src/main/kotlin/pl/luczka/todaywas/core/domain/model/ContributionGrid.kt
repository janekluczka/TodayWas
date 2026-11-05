package pl.luczka.todaywas.core.domain.model

import java.time.LocalDate

data class ContributionGrid(
    val window: ContributionWindow,
    val days: Map<LocalDate, ContributionLevel>,
)
