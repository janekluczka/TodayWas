package pl.luczka.todaywas.core.designsystem.components.selectioncontrols

import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsRangeSliderPreview() {
    DsTheme {
        DsRangeSlider(
            value = 0.25f..0.75f,
            onValueChange = {},
        )
    }
}
