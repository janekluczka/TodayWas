package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun TodayWasFloatingActionButtonPreview() {
    DesignSystemPreviewTheme {
        TodayWasFloatingActionButton(onClick = {}) {
            TodayWasIcon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
            )
        }
    }
}
