package pl.luczka.todaywas.feature.habit.impl.create

sealed interface CreateHabitNavigation {

    data object Saved : CreateHabitNavigation
}
