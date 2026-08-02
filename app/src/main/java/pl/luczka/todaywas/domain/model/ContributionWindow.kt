package pl.luczka.todaywas.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface ContributionWindow {

    data object RollingTwelveMonths : ContributionWindow

    data class CalendarYear(
        val year: Int,
    ) : ContributionWindow
}

fun ContributionWindow.dateRange(now: Instant): ClosedRange<LocalDate> {
    val today = LocalDate.ofInstant(now, ZoneId.systemDefault())
    return when (this) {
        ContributionWindow.RollingTwelveMonths -> today.minusMonths(12).plusDays(1)..today
        is ContributionWindow.CalendarYear -> LocalDate.of(year, 1, 1)..LocalDate.of(year, 12, 31)
    }
}

fun availableWindows(
    earliestDataDate: LocalDate?,
    now: Instant,
): List<ContributionWindow> {
    val currentYear = LocalDate.ofInstant(now, ZoneId.systemDefault()).year
    val earliestYear = earliestDataDate?.year ?: currentYear
    val years = (earliestYear..currentYear).reversed().map { ContributionWindow.CalendarYear(it) }
    return listOf(ContributionWindow.RollingTwelveMonths) + years
}
