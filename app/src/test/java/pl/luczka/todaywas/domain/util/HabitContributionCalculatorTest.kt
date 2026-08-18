package pl.luczka.todaywas.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.ContributionLevel
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.HabitCheckIn
import java.time.Instant
import java.time.LocalDate

class HabitContributionCalculatorTest {

    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val window = ContributionWindow.CalendarYear(2026)

    private fun checkIn(
        date: LocalDate,
        value: Int,
    ) = HabitCheckIn(
        id = date.toEpochDay().toString(),
        habitId = "1",
        date = date,
        value = value,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `should yield empty days when there are no check-ins`() {
        // Arrange
        val checkIns = emptyList<HabitCheckIn>()

        // Act
        val grid = HabitContributionCalculator.compute(checkIns, window, now)

        // Assert
        assertTrue(grid.days.isEmpty())
    }

    @Test
    fun `should spread distinct values across the full level range`() {
        // Arrange
        val checkIns = listOf(
            checkIn(LocalDate.of(2026, 1, 1), 1),
            checkIn(LocalDate.of(2026, 1, 2), 2),
            checkIn(LocalDate.of(2026, 1, 3), 3),
            checkIn(LocalDate.of(2026, 1, 4), 4),
            checkIn(LocalDate.of(2026, 1, 5), 5),
        )

        // Act
        val grid = HabitContributionCalculator.compute(checkIns, window, now)

        // Assert
        assertEquals(ContributionLevel.LEVEL_1, grid.days[LocalDate.of(2026, 1, 1)])
        assertEquals(ContributionLevel.LEVEL_2, grid.days[LocalDate.of(2026, 1, 2)])
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 3)])
        assertEquals(ContributionLevel.LEVEL_4, grid.days[LocalDate.of(2026, 1, 4)])
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 5)])
    }

    @Test
    fun `should map all identical values to the same top level`() {
        // Arrange
        val checkIns = listOf(
            checkIn(LocalDate.of(2026, 1, 1), 3),
            checkIn(LocalDate.of(2026, 1, 2), 3),
            checkIn(LocalDate.of(2026, 1, 3), 3),
        )

        // Act
        val grid = HabitContributionCalculator.compute(checkIns, window, now)

        // Assert
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 1)])
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 2)])
        assertEquals(ContributionLevel.LEVEL_5, grid.days[LocalDate.of(2026, 1, 3)])
    }

    @Test
    fun `should omit date from days when it has no check-in`() {
        // Arrange
        val checkIns = listOf(checkIn(LocalDate.of(2026, 1, 1), 1))

        // Act
        val grid = HabitContributionCalculator.compute(checkIns, window, now)

        // Assert
        assertTrue(grid.days[LocalDate.of(2026, 1, 2)] == null)
    }

    @Test
    fun `should still let out-of-window values shape the in-window rank when window filters dates`() {
        // Arrange
        // sortedValues = [1, 100]; for the in-window value=1, percentileRank = count(<=1)/2 = 0.5,
        // level = ceil(0.5 * 5) = 3 (LEVEL_3) — the out-of-window value=100 still pulls the
        // in-window day's rank down, even though 2025-06-01 itself never appears in `days`.
        val checkIns = listOf(
            checkIn(LocalDate.of(2025, 6, 1), 100),
            checkIn(LocalDate.of(2026, 1, 1), 1),
        )

        // Act
        val grid = HabitContributionCalculator.compute(checkIns, window, now)

        // Assert
        assertTrue(LocalDate.of(2025, 6, 1) !in grid.days)
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 1)])
    }
}
