package pl.luczka.todaywas.core.designsystem.components.textfields

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
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
private fun DsTextFieldPreview() {
    DesignSystemPreviewTheme {
        DsTextField(
            value = "Today was a good day.",
            onValueChange = {},
            label = "Entry",
            minLines = 4,
        )
    }
}
