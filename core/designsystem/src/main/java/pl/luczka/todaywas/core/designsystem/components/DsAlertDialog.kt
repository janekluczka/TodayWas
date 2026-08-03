package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsAlertDialogPreview() {
    DesignSystemPreviewTheme {
        DsAlertDialog(
            onDismissRequest = {},
            confirmButton = { DsTextButton(text = "Confirm", onClick = {}) },
            dismissButton = { DsTextButton(text = "Cancel", onClick = {}) },
            title = { DsText(text = "Delete entry?") },
            text = { DsText(text = "This can't be undone.") },
        )
    }
}
