package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasChip(
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
        label = { TodayWasText(text = text, color = if (selected) selectedLabelColor else labelColor) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            selectedContainerColor = selectedContainerColor,
        ),
        border = null,
        modifier = modifier,
    )
}

private class TodayWasChipSelectedPreviewProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(false, true)
}

@PreviewLightDark
@Composable
private fun TodayWasChipPreview(
    @PreviewParameter(TodayWasChipSelectedPreviewProvider::class) selected: Boolean,
) {
    DesignSystemPreviewTheme {
        TodayWasChip(text = "Done", selected = selected)
    }
}
