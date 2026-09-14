package pl.luczka.todaywas.ui.habit

import androidx.compose.runtime.Composable
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionValueBadge
import pl.luczka.todaywas.core.designsystem.components.lists.DsListItem
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitUiState

// Shared by Main's capped Habit section and the full Habit list screen — a colored cell matching
// the habit's own contribution grid, with today's value drawn inside, in place of plain text.
@Composable
fun HabitRow(
    habit: HabitUiState,
    onClick: () -> Unit,
) {
    DsListItem(
        text = habit.name,
        trailingContent = {
            DsContributionValueBadge(
                level = habit.todayLevel,
                valueText = habit.todayStatus.toValueText(),
            )
        },
        onClick = onClick,
    )
}

// The value badge is too small for localized "Done"/"Not done" text, so it shows the raw value
// for both binary and scale habits, and nothing for an unlogged day.
private fun HabitCheckInStatusUiState.toValueText(): String = when (this) {
    HabitCheckInStatusUiState.NotLogged -> ""
    is HabitCheckInStatusUiState.LoggedBinary -> if (done) "1" else "0"
    is HabitCheckInStatusUiState.LoggedScale -> value.toString()
}
