package pl.luczka.todaywas.ui.habit.detail

import java.time.Instant
import java.time.LocalDate
import pl.luczka.todaywas.core.domain.model.Habit
import pl.luczka.todaywas.core.domain.model.HabitCheckIn
import pl.luczka.todaywas.core.domain.model.HabitType
import pl.luczka.todaywas.core.ui.model.ContributionGridUiState

internal fun HabitDetailViewModelState.toUiState(
    freshLoggableDates: List<LocalDate>,
    isEditable: (Instant) -> Boolean,
    contributionGrid: ContributionGridUiState,
): HabitDetailUiState = HabitDetailUiState(
    isLoading = isLoading,
    habitName = habitName,
    type = type,
    range = range,
    selectedDate = selectedDate,
    selectedDay = checkIns.toSelectedDayUiState(selectedDate, freshLoggableDates, isEditable),
    checkInCount = checkIns.size,
    contributionGrid = contributionGrid,
    editingDate = editingDate,
    editingValue = editingValue,
    isSaving = isSaving,
    saveError = saveError,
    saveErrorIsWindowExpired = saveErrorIsWindowExpired,
    isDeleteHabitDialogVisible = isDeleteHabitDialogVisible,
    isDeletingHabit = isDeletingHabit,
    deleteHabitError = deleteHabitError,
    isDeletingCheckIn = isDeletingCheckIn,
    deleteCheckInError = deleteCheckInError,
    pendingNavigation = pendingNavigation,
)

// Today/yesterday are addable even unlogged (existing == null but selectedDate is one of
// freshLoggableDates) — every other unlogged date is view-only, no action. A logged date is
// editable only within the 24h edit window; deletion is always allowed regardless of age, so it
// doesn't gate on eligibleForEdit at all (see alreadyLogged on the caller side).
fun List<HabitCheckIn>.toSelectedDayUiState(
    selectedDate: LocalDate,
    freshLoggableDates: List<LocalDate>,
    isEditable: (Instant) -> Boolean,
): HabitDetailDayUiState {
    val existing = find { it.date == selectedDate }
    return HabitDetailDayUiState(
        date = selectedDate,
        value = existing?.value,
        eligibleForEdit = if (existing != null) {
            isEditable(existing.createdAt)
        } else {
            selectedDate in freshLoggableDates
        },
        alreadyLogged = existing != null,
    )
}

fun Habit.detailRange(): IntRange = if (type == HabitType.BINARY) {
    0..1
} else {
    (scaleMin ?: 0)..(scaleMax ?: 0)
}
