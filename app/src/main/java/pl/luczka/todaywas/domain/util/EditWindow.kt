package pl.luczka.todaywas.domain.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object EditWindow {

    val HOURS: Duration = Duration.ofHours(24)

    fun isEditable(
        createdAt: Instant,
        now: Instant,
    ): Boolean = Duration.between(createdAt, now) < HOURS

    // Today + yesterday, in the device's default zone - the fixed window a fresh (not-yet-logged)
    // habit check-in or journal entry can be backdated into.
    fun freshLoggableDates(now: Instant): List<LocalDate> {
        val today = LocalDate.ofInstant(now, ZoneId.systemDefault())
        return listOf(today, today.minusDays(1))
    }
}
