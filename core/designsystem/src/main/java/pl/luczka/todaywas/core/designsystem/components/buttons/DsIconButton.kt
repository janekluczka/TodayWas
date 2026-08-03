package pl.luczka.todaywas.core.designsystem.components.buttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        content = content,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsIconButtonPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) enabled: Boolean,
) {
    DesignSystemPreviewTheme {
        DsIconButton(
            onClick = {},
            enabled = enabled,
        ) {
            DsIcon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Change focus",
            )
        }
    }
}
