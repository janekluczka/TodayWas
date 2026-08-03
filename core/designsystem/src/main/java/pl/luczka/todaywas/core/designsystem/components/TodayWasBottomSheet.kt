package pl.luczka.todaywas.core.designsystem.components

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
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayWasBottomSheet(
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TodayWasIconButton(onClick = onCloseClicked) {
                TodayWasIcon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeContentDescription,
                )
            }
            TodayWasButtonWithLoading(
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
private fun TodayWasBottomSheetPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) isSaving: Boolean,
) {
    DesignSystemPreviewTheme {
        TodayWasBottomSheet(
            onDismissRequest = {},
            onCloseClicked = {},
            closeContentDescription = "Close",
            saveText = "Save",
            onSaveClicked = {},
            isSaving = isSaving,
        ) {
            TodayWasText(
                text = "Sheet content",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
