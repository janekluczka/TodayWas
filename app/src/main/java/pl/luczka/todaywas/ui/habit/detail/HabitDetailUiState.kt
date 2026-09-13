package pl.luczka.todaywas.ui.habit.detail

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import java.time.LocalDate

@Immutable
data class HabitDetailUiState(
    val isLoading: Boolean,
    val habitName: String,
    val type: HabitTypeUiState,
    val range: IntRange,
    val selectedDate: LocalDate,
    val selectedDay: HabitDetailDayUiState,
    // Total check-ins ever logged for this habit, independent of the currently selected day —
    // used only by the delete-habit confirmation dialog's "this will delete N check-ins" warning.
    val checkInCount: Int,
    val contributionGrid: ContributionGridUiState,
    val availableWindows: List<ContributionWindowUiState>,
    val selectedWindow: ContributionWindowUiState,
    val editingDate: LocalDate? = null,
    val editingValue: Int? = null,
    val isSaving: Boolean,
    val saveError: Boolean,
    val saveErrorIsWindowExpired: Boolean,
    val isDeleteHabitDialogVisible: Boolean = false,
    val isDeletingHabit: Boolean = false,
    val deleteHabitError: Boolean = false,
    val isDeletingCheckIn: Boolean = false,
    val deleteCheckInError: Boolean = false,
) {
    val isEditSheetOpen: Boolean get() = editingDate != null
}

data class HabitDetailDayUiState(
    val date: LocalDate,
    val value: Int?,
    val eligibleForEdit: Boolean,
    val alreadyLogged: Boolean,
)
