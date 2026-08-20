package pl.luczka.todaywas.ui.habit.logcheckin

import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitCheckInBoard
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.ui.mapper.toUiState
import java.time.LocalDate

fun HabitCheckInBoard.toRows(
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

private fun Habit.toEditableRow(pendingValue: Int?): HabitCheckInRowUiState.Editable =
    HabitCheckInRowUiState.Editable(
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
