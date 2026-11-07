package pl.luczka.todaywas.feature.habit.impl.logcheckin

import java.time.LocalDate
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.domain.habit.model.Habit
import pl.luczka.todaywas.domain.habit.model.HabitCheckIn
import pl.luczka.todaywas.domain.habit.model.HabitCheckInBoard
import pl.luczka.todaywas.domain.habit.model.HabitType

internal fun HabitCheckInBoard.toRows(
    selectedDate: LocalDate,
    pendingValues: Map<String, Int>,
): List<HabitCheckInRowUiState> = habits.map { habit ->
    val existing = checkIns.find { it.habitId == habit.id && it.date == selectedDate }
    if (existing != null) {
        habit.toAlreadyLoggedRow(existing)
    } else {
        habit.toEditableRow(pendingValues[habit.id])
    }
}

private fun Habit.toAlreadyLoggedRow(checkIn: HabitCheckIn): HabitCheckInRowUiState.AlreadyLogged =
    HabitCheckInRowUiState.AlreadyLogged(
        habitId = id,
        name = name,
        range = habitRange(),
        type = type.toUiState(),
        value = checkIn.value,
    )

private fun Habit.toEditableRow(pendingValue: Int?): HabitCheckInRowUiState.Editable = HabitCheckInRowUiState.Editable(
    habitId = id,
    name = name,
    range = habitRange(),
    type = type.toUiState(),
    value = pendingValue,
)

private fun Habit.habitRange(): IntRange = if (type == HabitType.BINARY) {
    0..1
} else {
    (scaleMin ?: 0)..(scaleMax ?: 0)
}
