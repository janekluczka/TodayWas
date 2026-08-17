package pl.luczka.todaywas.ui.habit.detail

import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import java.time.LocalDate

sealed interface HabitDetailIntent {

    data object EditClicked : HabitDetailIntent

    data class ValueChanged(
        val date: LocalDate,
        val value: Int?,
    ) : HabitDetailIntent

    data object SaveClicked : HabitDetailIntent

    data object CancelEditClicked : HabitDetailIntent

    data object BackClicked : HabitDetailIntent

    data class WindowSelected(
        val window: ContributionWindowUiState,
    ) : HabitDetailIntent

    data object DeleteHabitClicked : HabitDetailIntent

    data object DeleteHabitConfirmed : HabitDetailIntent

    data object DeleteHabitDismissed : HabitDetailIntent

    data class DeleteCheckInClicked(
        val date: LocalDate,
    ) : HabitDetailIntent

    data object DeleteCheckInConfirmed : HabitDetailIntent

    data object DeleteCheckInDismissed : HabitDetailIntent
}
