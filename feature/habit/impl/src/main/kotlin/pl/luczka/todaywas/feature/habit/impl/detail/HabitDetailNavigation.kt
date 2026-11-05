package pl.luczka.todaywas.feature.habit.impl.detail

sealed interface HabitDetailNavigation {

    data object HabitDeleted : HabitDetailNavigation
}
