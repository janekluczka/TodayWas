package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitCheckInBoard
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import java.time.LocalDate

fun HabitCheckInBoard.toHabitUiStates(today: LocalDate): List<HabitUiState> = habits.map { habit ->
    val todayCheckIn = checkIns.find { it.habitId == habit.id && it.date == today }
    habit.toUiState(todayCheckIn)
}

fun Habit.toUiState(todayCheckIn: HabitCheckIn?): HabitUiState = HabitUiState(
    id = id,
    name = name,
    type = type.toUiState(),
    todayStatus = when {
        todayCheckIn == null -> HabitCheckInStatusUiState.NotLogged
        type == HabitType.BINARY -> HabitCheckInStatusUiState.LoggedBinary(
            done =
                todayCheckIn.value == 1,
        )
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
