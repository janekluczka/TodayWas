package pl.luczka.todaywas.ui.habit

import pl.luczka.todaywas.domain.model.EditWindow
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.Instant
import java.time.LocalDate

fun List<HabitCheckIn>.toHabitDetailRows(
    dates: List<LocalDate>,
    pendingValues: Map<LocalDate, Int>,
    now: Instant,
): List<HabitDetailRowUiState> {
    val existingByDate = associateBy { it.date }
    return dates.map { date ->
        val existing = existingByDate[date]
        HabitDetailRowUiState(
            date = date,
            value = pendingValues[date] ?: existing?.value,
            editable = existing == null || EditWindow.isEditable(existing.createdAt, now),
            alreadyLogged = existing != null,
        )
    }
}

fun Habit.detailRange(): IntRange = if (type == HabitType.BINARY) {
    0..1
} else {
    (scaleMin ?: 0)..(scaleMax ?: 0)
}
