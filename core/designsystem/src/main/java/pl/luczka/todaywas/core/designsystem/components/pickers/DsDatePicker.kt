package pl.luczka.todaywas.core.designsystem.components.pickers

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsDatePicker(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    state: DatePickerState = rememberDatePickerState(),
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = modifier,
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DsDatePickerPreview() {
    DsTheme {
        DsDatePicker(
            onDismissRequest = {},
            confirmButton = { DsTextButton(text = "OK", onClick = {}) },
            dismissButton = { DsTextButton(text = "Cancel", onClick = {}) },
        )
    }
}
