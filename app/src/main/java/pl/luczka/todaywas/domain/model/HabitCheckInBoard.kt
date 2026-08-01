package pl.luczka.todaywas.domain.model

data class HabitCheckInBoard(
    val habits: List<Habit>,
    val checkIns: List<HabitCheckIn>,
)
