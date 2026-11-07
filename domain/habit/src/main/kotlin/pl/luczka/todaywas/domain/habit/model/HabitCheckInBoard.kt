package pl.luczka.todaywas.domain.habit.model

data class HabitCheckInBoard(
    val habits: List<Habit>,
    val checkIns: List<HabitCheckIn>,
)
