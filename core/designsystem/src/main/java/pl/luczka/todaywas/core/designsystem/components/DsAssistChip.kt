package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsAssistChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        label = { DsText(text = text) },
        enabled = enabled,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsAssistChipPreview() {
    DesignSystemPreviewTheme {
        DsAssistChip(
            text = "Regenerate",
            onClick = {},
        )
    }
}
