package pl.luczka.todaywas.ui.habit.detail

import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.Instant
import java.time.LocalDate

fun List<HabitCheckIn>.toHabitDetailRows(
    freshLoggableDates: List<LocalDate>,
    isEditable: (Instant) -> Boolean,
): List<HabitDetailRowUiState> {
    val existingByDate = associateBy { it.date }
    // Today/yesterday always show, even unlogged, so they stay reachable to add a value; every
    // other date only appears once it actually has a check-in — this is the full history, not a
    // fixed backfill window.
    val dates = (existingByDate.keys + freshLoggableDates).sortedDescending()
    return dates.map { date ->
        val existing = existingByDate[date]
        HabitDetailRowUiState(
            date = date,
            value = existing?.value,
            eligibleForEdit = existing == null || isEditable(existing.createdAt),
            alreadyLogged = existing != null,
        )
    }
}

fun Habit.detailRange(): IntRange = if (type == HabitType.BINARY) {
    0..1
} else {
    (scaleMin ?: 0)..(scaleMax ?: 0)
}
