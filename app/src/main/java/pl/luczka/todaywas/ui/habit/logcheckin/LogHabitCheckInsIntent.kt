package pl.luczka.todaywas.ui.habit.logcheckin

import java.time.LocalDate

sealed interface LogHabitCheckInsIntent {

    data class DateSelected(
        val date: LocalDate,
    ) : LogHabitCheckInsIntent

    data class ValueChanged(
        val habitId: String,
        val value: Int?,
    ) : LogHabitCheckInsIntent

    data object SaveClicked : LogHabitCheckInsIntent

    data object CancelClicked : LogHabitCheckInsIntent
}
