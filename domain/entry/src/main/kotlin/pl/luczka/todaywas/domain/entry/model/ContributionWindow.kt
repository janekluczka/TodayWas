package pl.luczka.todaywas.domain.entry.model

import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId

sealed interface ContributionWindow {

    data object RollingTwelveMonths : ContributionWindow

    data class CalendarYear(
        val year: Int,
    ) : ContributionWindow
}

fun ContributionWindow.dateRange(now: Instant): ClosedRange<LocalDate> {
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    return when (this) {
        ContributionWindow.RollingTwelveMonths -> today.minusYears(1).plusDays(1)..today
        is ContributionWindow.CalendarYear -> Year.of(year).let { it.atDay(1)..it.atDay(it.length()) }
    }
}

fun availableWindows(
    earliestDataDate: LocalDate?,
    now: Instant,
): List<ContributionWindow> {
    val currentYear = now.atZone(ZoneId.systemDefault()).year
    val earliestYear = earliestDataDate?.year ?: currentYear
    val years = (earliestYear..currentYear).reversed().map { ContributionWindow.CalendarYear(it) }
    return listOf(ContributionWindow.RollingTwelveMonths) + years
}
