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
    val rows: List<HabitDetailRowUiState>,
    val contributionGrid: ContributionGridUiState,
    val availableWindows: List<ContributionWindowUiState>,
    val selectedWindow: ContributionWindowUiState,
    val isEditSheetOpen: Boolean,
    val isSaving: Boolean,
    val saveError: Boolean,
    val saveErrorIsWindowExpired: Boolean,
)

data class HabitDetailRowUiState(
    val date: LocalDate,
    val value: Int?,
    val eligibleForEdit: Boolean,
    val alreadyLogged: Boolean,
)
