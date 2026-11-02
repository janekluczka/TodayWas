package pl.luczka.todaywas.ui.habit.detail

sealed interface HabitDetailNavigation {

    data object HabitDeleted : HabitDetailNavigation
}
