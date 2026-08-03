package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsSwitchPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) checked: Boolean,
) {
    DesignSystemPreviewTheme {
        DsSwitch(
            checked = checked,
            onCheckedChange = {},
        )
    }
}
