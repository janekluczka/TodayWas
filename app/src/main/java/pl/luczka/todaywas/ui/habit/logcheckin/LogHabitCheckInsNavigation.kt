package pl.luczka.todaywas.ui.habit.logcheckin

sealed interface LogHabitCheckInsNavigation {

    data object Saved : LogHabitCheckInsNavigation
}
