package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasTextField(
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
        modifier = modifier,
        label = label?.let { { TodayWasText(text = it) } },
        enabled = enabled,
        minLines = minLines,
    )
}

@PreviewLightDark
@Composable
private fun TodayWasTextFieldPreview() {
    DesignSystemPreviewTheme {
        TodayWasTextField(
            value = "Today was a good day.",
            onValueChange = {},
            label = "Entry",
            minLines = 4,
        )
    }
}
