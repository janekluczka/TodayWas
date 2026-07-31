package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType

fun Habit.toUiState(todayCheckIn: HabitCheckIn?): HabitUiState = HabitUiState(
    id = id,
    name = name,
    type = type.toUiState(),
    todayStatus = when {
        todayCheckIn == null -> HabitCheckInStatusUiState.NotLogged
        type == HabitType.BINARY -> HabitCheckInStatusUiState.LoggedBinary(done = todayCheckIn.value == 1)
        else -> HabitCheckInStatusUiState.LoggedScale(value = todayCheckIn.value)
    },
)

fun HabitType.toUiState(): HabitTypeUiState = when (this) {
    HabitType.BINARY -> HabitTypeUiState.BINARY
    HabitType.SCALE -> HabitTypeUiState.SCALE
}

fun HabitTypeUiState.toDomain(): HabitType = when (this) {
    HabitTypeUiState.BINARY -> HabitType.BINARY
    HabitTypeUiState.SCALE -> HabitType.SCALE
}
