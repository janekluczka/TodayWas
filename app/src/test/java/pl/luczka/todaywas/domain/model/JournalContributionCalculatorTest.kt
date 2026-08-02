package pl.luczka.todaywas.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class JournalContributionCalculatorTest {

    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val window = ContributionWindow.CalendarYear(2026)

    private fun entry(date: LocalDate) = JournalEntry(id = date.toEpochDay(), date = date, text = "entry", createdAt = now)

    @Test
    fun `date with an entry maps to level 3`() {
        val entries = listOf(entry(LocalDate.of(2026, 1, 1)))
        val grid = JournalContributionCalculator.compute(entries, window, now)
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 1)])
    }

    @Test
    fun `date with no entry is absent from days`() {
        val entries = listOf(entry(LocalDate.of(2026, 1, 1)))
        val grid = JournalContributionCalculator.compute(entries, window, now)
        assertTrue(LocalDate.of(2026, 1, 2) !in grid.days)
    }

    @Test
    fun `window filters out entries outside the range`() {
        val entries = listOf(entry(LocalDate.of(2025, 6, 1)), entry(LocalDate.of(2026, 1, 1)))
        val grid = JournalContributionCalculator.compute(entries, window, now)
        assertTrue(LocalDate.of(2025, 6, 1) !in grid.days)
        assertEquals(ContributionLevel.LEVEL_3, grid.days[LocalDate.of(2026, 1, 1)])
    }
}
