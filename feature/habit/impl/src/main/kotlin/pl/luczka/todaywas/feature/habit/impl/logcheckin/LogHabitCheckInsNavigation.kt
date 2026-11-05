package pl.luczka.todaywas.feature.habit.impl.logcheckin

sealed interface LogHabitCheckInsNavigation {

    data object Saved : LogHabitCheckInsNavigation
}
