package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { DsText(text = it) } },
        enabled = enabled,
        minLines = minLines,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsFilledTextFieldPreview() {
    DesignSystemPreviewTheme {
        DsFilledTextField(
            value = "Today was a good day.",
            onValueChange = {},
            label = "Entry",
            minLines = 4,
        )
    }
}
