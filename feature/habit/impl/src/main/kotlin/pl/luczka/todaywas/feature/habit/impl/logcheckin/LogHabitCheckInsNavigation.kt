package pl.luczka.todaywas.feature.habit.impl.logcheckin

internal sealed interface LogHabitCheckInsNavigation {

    data object Saved : LogHabitCheckInsNavigation
}
