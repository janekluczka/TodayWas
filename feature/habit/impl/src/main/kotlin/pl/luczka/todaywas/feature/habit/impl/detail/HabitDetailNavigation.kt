package pl.luczka.todaywas.feature.habit.impl.detail

internal sealed interface HabitDetailNavigation {

    data object HabitDeleted : HabitDetailNavigation
}
