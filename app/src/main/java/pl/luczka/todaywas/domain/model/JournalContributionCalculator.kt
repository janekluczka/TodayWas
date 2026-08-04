package pl.luczka.todaywas.domain.model

import java.time.Instant

object JournalContributionCalculator {

    fun compute(
        entries: List<JournalEntry>,
        window: ContributionWindow,
        now: Instant,
    ): ContributionGrid {
        val range = window.dateRange(now)
        val entryDates = entries.map { it.date }.toSet()
        val days = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it <= range.endInclusive }
            .filter { it in entryDates }
            .associateWith { ContributionLevel.LEVEL_3 }
        return ContributionGrid(window = window, days = days)
    }
}
