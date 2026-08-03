package pl.luczka.todaywas.core.designsystem.components.pickers

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    state: TimePickerState = rememberTimePickerState(),
) {
    DsAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = { TimePicker(state = state) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DsTimePickerDialogPreview() {
    DsTheme {
        DsTimePickerDialog(
            onDismissRequest = {},
            confirmButton = { DsTextButton(text = "OK", onClick = {}) },
            dismissButton = { DsTextButton(text = "Cancel", onClick = {}) },
        )
    }
}
