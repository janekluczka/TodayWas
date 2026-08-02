package pl.luczka.todaywas.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ContributionWindowTest {

    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val today = LocalDate.of(2026, 8, 2)

    @Test
    fun `rolling twelve months spans trailing 12 months ending today`() {
        val range = ContributionWindow.RollingTwelveMonths.dateRange(now)
        assertEquals(today.minusMonths(12).plusDays(1), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun `calendar year spans jan 1 to dec 31 of that year`() {
        val range = ContributionWindow.CalendarYear(2025).dateRange(now)
        assertEquals(LocalDate.of(2025, 1, 1), range.start)
        assertEquals(LocalDate.of(2025, 12, 31), range.endInclusive)
    }

    @Test
    fun `available windows with no data is rolling plus current year only`() {
        val windows = availableWindows(earliestDataDate = null, now = now)
        assertEquals(
            listOf(ContributionWindow.RollingTwelveMonths, ContributionWindow.CalendarYear(2026)),
            windows,
        )
    }

    @Test
    fun `available windows with data in the current year only`() {
        val windows = availableWindows(earliestDataDate = LocalDate.of(2026, 1, 15), now = now)
        assertEquals(
            listOf(ContributionWindow.RollingTwelveMonths, ContributionWindow.CalendarYear(2026)),
            windows,
        )
    }

    @Test
    fun `available windows with multi-year data descends from current year`() {
        val windows = availableWindows(earliestDataDate = LocalDate.of(2024, 3, 1), now = now)
        assertEquals(
            listOf(
                ContributionWindow.RollingTwelveMonths,
                ContributionWindow.CalendarYear(2026),
                ContributionWindow.CalendarYear(2025),
                ContributionWindow.CalendarYear(2024),
            ),
            windows,
        )
    }
}
