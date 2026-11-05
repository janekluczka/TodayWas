package pl.luczka.todaywas.feature.habit.impl.create

internal sealed interface CreateHabitNavigation {

    data object Saved : CreateHabitNavigation
}
