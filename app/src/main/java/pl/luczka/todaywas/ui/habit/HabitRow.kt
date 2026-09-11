package pl.luczka.todaywas.ui.habit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitUiState

// Shared by Main's capped Habit section and the full Habit list screen.
@Composable
fun HabitRow(
    habit: HabitUiState,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(DsSpacing.space400),
    ) {
        DsText(text = habit.name)
        DsText(text = habit.todayStatus.displayText())
    }
}

@Composable
private fun HabitCheckInStatusUiState.displayText(): String = when (this) {
    HabitCheckInStatusUiState.NotLogged ->
        stringResource(R.string.habit_checkin_not_logged_label)
    is HabitCheckInStatusUiState.LoggedBinary ->
        stringResource(
            if (done) R.string.habit_checkin_done_label else R.string.habit_checkin_not_done_label,
        )
    is HabitCheckInStatusUiState.LoggedScale -> value.toString()
}
