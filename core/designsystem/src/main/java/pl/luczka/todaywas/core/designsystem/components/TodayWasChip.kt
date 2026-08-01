package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    labelColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    SuggestionChip(
        onClick = {},
        label = { TodayWasText(text = text, color = labelColor) },
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = containerColor),
        border = null,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun TodayWasChipPreview() {
    DesignSystemPreviewTheme {
        TodayWasChip(text = "Done")
    }
}
