package pl.luczka.todaywas.core.designsystem.components.buttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

// A filled-container icon button, for controls that need to read clearly as tappable on their own
// (e.g. stepper +/- buttons) -- unlike DsIconButton's borderless M3 IconButton, meant for app bars
// and rows where a bare icon is enough.
@Composable
fun DsFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        content = content,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsFilledTonalIconButtonPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) enabled: Boolean,
) {
    DsTheme {
        DsFilledTonalIconButton(
            onClick = {},
            enabled = enabled,
        ) {
            DsIcon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
            )
        }
    }
}
