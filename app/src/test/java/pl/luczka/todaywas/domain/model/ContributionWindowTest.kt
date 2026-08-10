package pl.luczka.todaywas.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ContributionWindowTest {

    private val now = Instant.parse("2026-08-02T12:00:00Z")
    private val today = LocalDate.of(2026, 8, 2)

    @Test
    fun `should span trailing 12 months ending today when window is RollingTwelveMonths`() {
        // Arrange
        val window = ContributionWindow.RollingTwelveMonths

        // Act
        val range = window.dateRange(now)

        // Assert
        assertEquals(today.minusMonths(12).plusDays(1), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun `should span Jan 1 to Dec 31 of that year when window is CalendarYear`() {
        // Arrange
        val window = ContributionWindow.CalendarYear(2025)

        // Act
        val range = window.dateRange(now)

        // Assert
        assertEquals(LocalDate.of(2025, 1, 1), range.start)
        assertEquals(LocalDate.of(2025, 12, 31), range.endInclusive)
    }

    @Test
    fun `should return rolling plus current year only when there is no earliest data date`() {
        // Arrange
        val earliestDataDate: LocalDate? = null

        // Act
        val windows = availableWindows(earliestDataDate = earliestDataDate, now = now)

        // Assert
        assertEquals(
            listOf(ContributionWindow.RollingTwelveMonths, ContributionWindow.CalendarYear(2026)),
            windows,
        )
    }

    @Test
    fun `should return rolling plus current year only when earliest data date is in the current year`() {
        // Arrange
        val earliestDataDate = LocalDate.of(2026, 1, 15)

        // Act
        val windows = availableWindows(earliestDataDate = earliestDataDate, now = now)

        // Assert
        assertEquals(
            listOf(ContributionWindow.RollingTwelveMonths, ContributionWindow.CalendarYear(2026)),
            windows,
        )
    }

    @Test
    fun `should return windows descending from current year when data spans multiple years`() {
        // Arrange
        val earliestDataDate = LocalDate.of(2024, 3, 1)

        // Act
        val windows = availableWindows(earliestDataDate = earliestDataDate, now = now)

        // Assert
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
