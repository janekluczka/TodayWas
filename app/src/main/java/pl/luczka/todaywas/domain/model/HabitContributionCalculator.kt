package pl.luczka.todaywas.domain.model

import java.time.Instant
import kotlin.math.ceil

object HabitContributionCalculator {

    fun compute(
        checkIns: List<HabitCheckIn>,
        window: ContributionWindow,
        now: Instant,
    ): ContributionGrid {
        val range = window.dateRange(now)
        val checkInsByDate = checkIns.associateBy { it.date }
        val sortedValues = checkIns.map { it.value }.sorted()
        val days = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it <= range.endInclusive }
            .mapNotNull { date ->
                val checkIn = checkInsByDate[date] ?: return@mapNotNull null
                date to levelFor(checkIn.value, sortedValues)
            }.toMap()
        return ContributionGrid(window = window, days = days)
    }

    private fun levelFor(
        value: Int,
        sortedValues: List<Int>,
    ): ContributionLevel {
        val percentileRank = sortedValues.count { it <= value }.toDouble() / sortedValues.size
        val level = ceil(percentileRank * 5).toInt().coerceIn(1, 5)
        return ContributionLevel.entries[level]
    }
}
