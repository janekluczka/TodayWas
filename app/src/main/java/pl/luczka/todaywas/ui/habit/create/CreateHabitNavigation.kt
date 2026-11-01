package pl.luczka.todaywas.ui.habit.create

sealed interface CreateHabitNavigation {

    data object Saved : CreateHabitNavigation
}
