package pl.luczka.todaywas.domain.util

import pl.luczka.todaywas.domain.model.ContributionGrid
import pl.luczka.todaywas.domain.model.ContributionLevel
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.dateRange
import java.time.Instant
import java.time.LocalDate
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

    // Cheaper than compute() for callers that only need one day's level (e.g. a habit list row's
    // "today" badge) — skips the whole-window date-range walk (compute() always iterates every
    // day in the window even when only one date is asked for), while using the exact same
    // levelFor() ranking so results stay identical to what a real grid would show for that date.
    fun levelForDate(
        checkIns: List<HabitCheckIn>,
        date: LocalDate,
    ): ContributionLevel? {
        val checkIn = checkIns.find { it.date == date } ?: return null
        val sortedValues = checkIns.map { it.value }.sorted()
        return levelFor(checkIn.value, sortedValues)
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
