package pl.luczka.todaywas.feature.habit.impl.detail

import java.time.LocalDate
import pl.luczka.todaywas.core.ui.model.HabitTypeUiState
import pl.luczka.todaywas.domain.habit.model.HabitCheckIn

// HabitDetailViewModel's internal state; it keeps domain check-ins and is mapped to
// HabitDetailUiState (HabitDetailMapper.kt) before anything reaches the screen.
internal data class HabitDetailViewModelState(
    val isLoading: Boolean = true,
    val habitName: String = "",
    val type: HabitTypeUiState = HabitTypeUiState.BINARY,
    val range: IntRange = 0..0,
    val checkIns: List<HabitCheckIn> = emptyList(),
    val selectedDate: LocalDate,
    val editingDate: LocalDate? = null,
    val editingValue: Int? = null,
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
    val saveErrorIsWindowExpired: Boolean = false,
    val isDeleteHabitDialogVisible: Boolean = false,
    val isDeletingHabit: Boolean = false,
    val deleteHabitError: Boolean = false,
    val isDeletingCheckIn: Boolean = false,
    val deleteCheckInError: Boolean = false,
    val pendingNavigation: HabitDetailNavigation? = null,
)
