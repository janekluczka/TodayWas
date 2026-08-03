package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    Slider(
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
private fun DsSliderPreview() {
    DesignSystemPreviewTheme {
        DsSlider(
            value = 0.5f,
            onValueChange = {},
        )
    }
}
