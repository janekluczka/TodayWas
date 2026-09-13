package pl.luczka.todaywas.ui.habit.detail

import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import java.time.Instant
import java.time.LocalDate

// Today/yesterday are addable even unlogged (existing == null but selectedDate is one of
// freshLoggableDates) — every other unlogged date is view-only, no action. A logged date is
// editable only within the 24h edit window; deletion is always allowed regardless of age, so it
// doesn't gate on eligibleForEdit at all (see alreadyLogged on the caller side).
fun List<HabitCheckIn>.toSelectedDayUiState(
    selectedDate: LocalDate,
    freshLoggableDates: List<LocalDate>,
    isEditable: (Instant) -> Boolean,
): HabitDetailDayUiState {
    val existing = find { it.date == selectedDate }
    return HabitDetailDayUiState(
        date = selectedDate,
        value = existing?.value,
        eligibleForEdit = if (existing != null) {
            isEditable(existing.createdAt)
        } else {
            selectedDate in freshLoggableDates
        },
        alreadyLogged = existing != null,
    )
}

fun Habit.detailRange(): IntRange = if (type == HabitType.BINARY) {
    0..1
} else {
    (scaleMin ?: 0)..(scaleMax ?: 0)
}
