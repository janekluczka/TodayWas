package pl.luczka.todaywas.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HabitContributionCalculatorTest {

    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val window = ContributionWindow.CalendarYear(2026)

    private fun checkIn(
        date: LocalDate,
        value: Int,
    ) = HabitCheckIn(id = date.toEpochDay(), habitId = 1L, date = date, value = value, createdAt = now)

    @Test
    fun `empty check-ins yields empty days`() {
        val grid = HabitContributionCalculator.compute(emptyList(), window, now)
        assertTrue(grid.days.isEmpty())
    }

    @Test
    fun `distinct values spread across the full level range`() {
        val checkIns = listOf(
            checkIn(LocalDate.of(2026, 1, 1), 1),
            checkIn(LocalDate.of(2026, 1, 2), 2),
            checkIn(LocalDate.of(2026, 1, 3), 3),
            checkIn(LocalDate.of(2026, 1, 4), 4),
            checkIn(LocalDate.of(2026, 1, 5), 5),
        )
        val grid = HabitContributionCalculator.compute(checkIns, window, now)
        assertEquals(ContributionLevel.LEVEL_1, grid.days[LocalDate.of(2026, 1, 1)])
        assertEquals(ContributionLevel.LEVEL_2, grid.days[LocalDate.of(2026, 1, 2)])
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 3)])
        assertEquals(ContributionLevel.LEVEL_4, grid.days[LocalDate.of(2026, 1, 4)])
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 5)])
    }

    @Test
    fun `identical values all map to the same top level`() {
        val checkIns = listOf(
            checkIn(LocalDate.of(2026, 1, 1), 3),
            checkIn(LocalDate.of(2026, 1, 2), 3),
            checkIn(LocalDate.of(2026, 1, 3), 3),
        )
        val grid = HabitContributionCalculator.compute(checkIns, window, now)
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 1)])
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 2)])
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 3)])
    }

    @Test
    fun `date with no check-in is absent from days`() {
        val checkIns = listOf(checkIn(LocalDate.of(2026, 1, 1), 1))
        val grid = HabitContributionCalculator.compute(checkIns, window, now)
        assertTrue(grid.days[LocalDate.of(2026, 1, 2)] == null)
    }

    @Test
    fun `window filters out-of-range dates but their values still shape the all-time range`() {
        // sortedValues = [1, 100]; for the in-window value=1, percentileRank = count(<=1)/2 = 0.5,
        // level = ceil(0.5 * 5) = 3 (LEVEL_3) — the out-of-window value=100 still pulls the
        // in-window day's rank down, even though 2025-06-01 itself never appears in `days`.
        val checkIns = listOf(
            checkIn(LocalDate.of(2025, 6, 1), 100),
            checkIn(LocalDate.of(2026, 1, 1), 1),
        )
        val grid = HabitContributionCalculator.compute(checkIns, window, now)
        assertTrue(LocalDate.of(2025, 6, 1) !in grid.days)
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 1)])
    }
}
