package pl.luczka.todaywas.core.designsystem.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsBottomSheet(
    onDismissRequest: () -> Unit,
    onCloseClicked: () -> Unit,
    closeContentDescription: String,
    saveText: String,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.space400, vertical = DsSpacing.space200),
        ) {
            DsIconButton(onClick = onCloseClicked) {
                DsIcon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeContentDescription,
                )
            }
            DsButtonWithLoading(
                text = saveText,
                onClick = onSaveClicked,
                enabled = saveEnabled && !isSaving,
                loading = isSaving,
            )
        }
        content()
    }
}

@PreviewLightDark
@Composable
private fun DsBottomSheetPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) isSaving: Boolean,
) {
    DsTheme {
        DsBottomSheet(
            onDismissRequest = {},
            onCloseClicked = {},
            closeContentDescription = "Close",
            saveText = "Save",
            onSaveClicked = {},
            isSaving = isSaving,
        ) {
            DsText(
                text = "Sheet content",
                modifier = Modifier.padding(DsSpacing.space400),
            )
        }
    }
}
