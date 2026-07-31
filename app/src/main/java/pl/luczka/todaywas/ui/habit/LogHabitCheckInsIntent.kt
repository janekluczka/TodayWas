package pl.luczka.todaywas.ui.habit

import java.time.LocalDate

sealed interface LogHabitCheckInsIntent {

    data class DateSelected(
        val date: LocalDate,
    ) : LogHabitCheckInsIntent

    data class BinaryValueChanged(
        val habitId: Long,
        val value: Boolean,
    ) : LogHabitCheckInsIntent

    data class ScaleValueChanged(
        val habitId: Long,
        val value: Int,
    ) : LogHabitCheckInsIntent

    data object SaveClicked : LogHabitCheckInsIntent

    data object CancelClicked : LogHabitCheckInsIntent
}
