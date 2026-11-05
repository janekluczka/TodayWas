package pl.luczka.todaywas.core.domain.model

data class HabitCheckInBoard(
    val habits: List<Habit>,
    val checkIns: List<HabitCheckIn>,
)
