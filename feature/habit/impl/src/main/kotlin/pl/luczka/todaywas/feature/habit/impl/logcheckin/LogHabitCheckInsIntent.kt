package pl.luczka.todaywas.feature.habit.impl.logcheckin

import java.time.LocalDate

internal sealed interface LogHabitCheckInsIntent {

    data class DateSelected(
        val date: LocalDate,
    ) : LogHabitCheckInsIntent

    data class ValueChanged(
        val habitId: String,
        val value: Int?,
    ) : LogHabitCheckInsIntent

    data object SaveClicked : LogHabitCheckInsIntent

    data object NavigationHandled : LogHabitCheckInsIntent
}
