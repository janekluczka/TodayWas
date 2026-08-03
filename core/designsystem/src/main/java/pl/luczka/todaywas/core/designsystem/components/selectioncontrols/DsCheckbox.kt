package pl.luczka.todaywas.core.designsystem.components.selectioncontrols

import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsCheckboxPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) checked: Boolean,
) {
    DsTheme {
        DsCheckbox(
            checked = checked,
            onCheckedChange = {},
        )
    }
}
