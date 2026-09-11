package pl.luczka.todaywas.core.designsystem.components.chips

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsAssistChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { DsText(text = text) },
        enabled = enabled,
        leadingIcon = leadingIcon?.let {
            { DsIcon(imageVector = it, contentDescription = null) }
        },
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsAssistChipPreview() {
    DsTheme {
        DsAssistChip(
            text = "Regenerate",
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DsAssistChipWithIconPreview() {
    DsTheme {
        DsAssistChip(
            text = "Help me start",
            onClick = {},
            leadingIcon = Icons.Filled.AutoAwesome,
        )
    }
}
