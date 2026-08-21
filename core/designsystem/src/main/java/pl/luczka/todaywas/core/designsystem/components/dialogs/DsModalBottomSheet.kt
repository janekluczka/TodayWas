package pl.luczka.todaywas.core.designsystem.components.dialogs

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

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
        // ModalBottomSheet's own default (colorScheme.surfaceContainerLow) is a role DsTheme never
        // overrides, so it silently falls back to M3's baseline Purple instead of our palette —
        // same class of bug as DsCard's containerColor. surface is DsTheme's actual neutral tone
        // (white in light, near-black in dark).
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DsModalBottomSheetPreview() {
    DsTheme {
        DsModalBottomSheet(onDismissRequest = {}) {
            DsText(text = "Bottom sheet content")
        }
    }
}
