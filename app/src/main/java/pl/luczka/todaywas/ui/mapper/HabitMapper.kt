package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitCheckInBoard
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.util.HabitContributionCalculator
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitSortUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import java.time.LocalDate

// Sorts on domain data (Habit + HabitCheckIn) before mapping to HabitUiState, so the UI model
// never needs to carry createdAt/lastCheckInDate purely for ordering purposes.
fun HabitCheckInBoard.toSortedHabitUiStates(
    today: LocalDate,
    sort: HabitSortUiState,
): List<HabitUiState> {
    val sortedHabits = when (sort) {
        HabitSortUiState.RECENTLY_CHECKED_IN -> {
            val lastCheckInDateByHabitId = checkIns
                .groupBy { it.habitId }
                .mapValues { (_, habitCheckIns) -> habitCheckIns.maxOf { it.date } }
            habits.sortedWith(
                compareByDescending<Habit> { lastCheckInDateByHabitId[it.id] ?: LocalDate.MIN }
                    .thenBy { it.createdAt },
            )
        }
        HabitSortUiState.ALPHABETICAL -> habits.sortedBy { it.name }
        HabitSortUiState.DATE_CREATED -> habits.sortedBy { it.createdAt }
    }
    return sortedHabits.map { habit ->
        val habitCheckIns = checkIns.filter { it.habitId == habit.id }
        val todayCheckIn = habitCheckIns.find { it.date == today }
        val todayLevel = HabitContributionCalculator
            .levelForDate(habitCheckIns, today)
            ?.toUiState()
            ?: DsContributionLevel.NONE
        habit.toUiState(todayCheckIn, todayLevel)
    }
}

fun Habit.toUiState(
    todayCheckIn: HabitCheckIn?,
    todayLevel: DsContributionLevel = DsContributionLevel.NONE,
): HabitUiState = HabitUiState(
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
    todayLevel = todayLevel,
)

fun HabitType.toUiState(): HabitTypeUiState = when (this) {
    HabitType.BINARY -> HabitTypeUiState.BINARY
    HabitType.SCALE -> HabitTypeUiState.SCALE
}

fun HabitTypeUiState.toDomain(): HabitType = when (this) {
    HabitTypeUiState.BINARY -> HabitType.BINARY
    HabitTypeUiState.SCALE -> HabitType.SCALE
}
