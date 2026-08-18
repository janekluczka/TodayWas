package pl.luczka.todaywas.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.domain.model.ContributionLevel
import pl.luczka.todaywas.domain.model.ContributionWindow
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate

class JournalContributionCalculatorTest {

    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val window = ContributionWindow.CalendarYear(2026)

    private fun entry(date: LocalDate) =
        JournalEntry(id = date.toEpochDay().toString(), date = date, text = "entry", createdAt = now, updatedAt = now)

    @Test
    fun `should map date to level 3 when it has an entry`() {
        // Arrange
        val entries = listOf(entry(LocalDate.of(2026, 1, 1)))

        // Act
        val grid = JournalContributionCalculator.compute(entries, window, now)

        // Assert
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 1)])
    }

    @Test
    fun `should omit date from days when it has no entry`() {
        // Arrange
        val entries = listOf(entry(LocalDate.of(2026, 1, 1)))

        // Act
        val grid = JournalContributionCalculator.compute(entries, window, now)

        // Assert
        assertTrue(LocalDate.of(2026, 1, 2) !in grid.days)
    }

    @Test
    fun `should filter out entries outside the range when window is applied`() {
        // Arrange
        val entries = listOf(entry(LocalDate.of(2025, 6, 1)), entry(LocalDate.of(2026, 1, 1)))

        // Act
        val grid = JournalContributionCalculator.compute(entries, window, now)

        // Assert
        assertTrue(LocalDate.of(2025, 6, 1) !in grid.days)
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 1)])
    }
}
