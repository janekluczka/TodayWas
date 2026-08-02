package pl.luczka.todaywas.ui.habit

import java.time.LocalDate

sealed interface HabitDetailIntent {

    data class ValueChanged(
        val date: LocalDate,
        val value: Int?,
    ) : HabitDetailIntent

    data object SaveClicked : HabitDetailIntent

    data object BackClicked : HabitDetailIntent
}
