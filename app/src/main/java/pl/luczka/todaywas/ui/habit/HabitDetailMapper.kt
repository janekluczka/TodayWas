package pl.luczka.todaywas.ui.habit

import pl.luczka.todaywas.domain.model.EditWindow
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun List<HabitCheckIn>.toHabitDetailRows(
    pendingValues: Map<LocalDate, Int>,
    now: Instant,
): List<HabitDetailRowUiState> {
    val existingByDate = associateBy { it.date }
    val today = LocalDate.ofInstant(now, ZoneId.systemDefault())
    val yesterday = today.minusDays(1)
    // Today/yesterday always show, even unlogged, so they stay reachable to add a value; every
    // other date only appears once it actually has a check-in — this is the full history, not a
    // fixed backfill window.
    val dates = (existingByDate.keys + today + yesterday).sortedDescending()
    return dates.map { date ->
        val existing = existingByDate[date]
        HabitDetailRowUiState(
            date = date,
            value = pendingValues[date] ?: existing?.value,
            eligibleForEdit = existing == null || EditWindow.isEditable(existing.createdAt, now),
            alreadyLogged = existing != null,
        )
    }
}

fun Habit.detailRange(): IntRange = if (type == HabitType.BINARY) {
    0..1
} else {
    (scaleMin ?: 0)..(scaleMax ?: 0)
}
