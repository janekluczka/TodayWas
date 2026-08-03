package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DsModalBottomSheetPreview() {
    DesignSystemPreviewTheme {
        DsModalBottomSheet(onDismissRequest = {}) {
            DsText(text = "Bottom sheet content")
        }
    }
}
