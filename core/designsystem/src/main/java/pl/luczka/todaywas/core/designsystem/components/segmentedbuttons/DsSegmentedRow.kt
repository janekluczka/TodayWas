package pl.luczka.todaywas.core.designsystem.components.segmentedbuttons

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.cards.DsCardVariant
import pl.luczka.todaywas.core.designsystem.components.cards.DsSelectableCard
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

@Composable
fun <T> DsSegmentedRow(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowDeselect: Boolean = true,
    label: (T) -> String = { it.toString() },
) {
    // Animates smoothly when `items` grows/shrinks (e.g. a scale habit's step count changing),
    // so new segments ease in on the right rather than popping in abruptly.
    Row(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.space100),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        for (item in items) {
            val selected = item == selectedItem
            DsSelectableCard(
                onClick = { onItemSelected(if (allowDeselect && selected) null else item) },
                enabled = enabled,
                variant = if (selected) DsCardVariant.PRIMARY else DsCardVariant.NEUTRAL,
                modifier = Modifier.weight(1f),
            ) {
                DsText(text = label(item))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DsSegmentedRowBinaryPreview() {
    DsTheme {
        DsSegmentedRow(
            items = listOf(0, 1),
            selectedItem = 1,
            onItemSelected = {},
            label = { if (it == 1) "Done" else "Not done" },
        )
    }
}

@PreviewLightDark
@Composable
private fun DsSegmentedRowScalePreview() {
    DsTheme {
        DsSegmentedRow(
            items = (1..5).toList(),
            selectedItem = null,
            onItemSelected = {},
        )
    }
}
