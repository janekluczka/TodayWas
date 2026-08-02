package pl.luczka.todaywas.core.designsystem.components.chips

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    labelColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    selectedLabelColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { DsText(text = text, color = if (selected) selectedLabelColor else labelColor) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            selectedContainerColor = selectedContainerColor,
        ),
        border = null,
        modifier = modifier,
    )
}

private class DsChipSelectedPreviewProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(false, true)
}

@PreviewLightDark
@Composable
private fun DsChipPreview(
    @PreviewParameter(DsChipSelectedPreviewProvider::class) selected: Boolean,
) {
    DsTheme {
        DsChip(text = "Done", selected = selected)
    }
}
