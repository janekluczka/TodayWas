package pl.luczka.todaywas.core.designsystem.components.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

// A single-select row of DsFilterChips that wraps onto additional lines instead of squeezing
// every item into one row — for choices whose item count/label length can't be predicted to fit
// a single row's width (e.g. mood labels), unlike DsSegmentedRow's fixed, always-one-row
// equal-width items.
@Composable
fun <T> DsChoiceFlowRow(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowDeselect: Boolean = true,
    label: (T) -> String = { it.toString() },
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.space200),
        modifier = modifier,
    ) {
        for (item in items) {
            val selected = item == selectedItem
            DsFilterChip(
                text = label(item),
                selected = selected,
                onClick = { onItemSelected(if (allowDeselect && selected) null else item) },
                enabled = enabled,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DsChoiceFlowRowPreview() {
    DsTheme {
        DsChoiceFlowRow(
            items = listOf("Very bad", "Bad", "Neutral", "Good", "Very good"),
            selectedItem = "Good",
            onItemSelected = {},
        )
    }
}
