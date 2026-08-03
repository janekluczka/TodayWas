package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsInputChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    InputChip(
        selected = selected,
        onClick = onClick,
        label = { DsText(text = text) },
        enabled = enabled,
        trailingIcon = trailingIcon,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsInputChipPreview() {
    DesignSystemPreviewTheme {
        DsInputChip(
            text = "Drink water",
            selected = true,
            onClick = {},
        )
    }
}
