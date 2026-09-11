package pl.luczka.todaywas.ui.habit.detail

import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import java.time.LocalDate

sealed interface HabitDetailIntent {

    data class EditRowClicked(
        val date: LocalDate,
    ) : HabitDetailIntent

    // Applies to whichever date is currently being edited (HabitDetailUiState.editingDate) — only
    // one row can be open at a time, so the row itself doesn't need to travel with this intent.
    // A null value on an already-logged row means "deselected", which deletes that check-in
    // immediately (see HabitDetailViewModel.onValueChanged) rather than just clearing the pick.
    data class ValueChanged(
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

    // Deletes immediately, no confirmation — this is the only delete path for a row outside the
    // edit window (eligibleForEdit false), since ValueChanged's deselect-to-delete shortcut needs
    // the row's value control enabled to be reachable at all.
    data class DeleteCheckInClicked(
        val date: LocalDate,
    ) : HabitDetailIntent
}
