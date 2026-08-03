package pl.luczka.todaywas.core.designsystem.components.fab

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        content = content,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsFloatingActionButtonPreview() {
    DesignSystemPreviewTheme {
        DsFloatingActionButton(onClick = {}) {
            DsIcon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
            )
        }
    }
}
