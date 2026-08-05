package pl.luczka.todaywas.core.designsystem.components.textfields

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { DsText(text = it) } },
        enabled = enabled,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { { DsText(text = it) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsTextFieldPreview() {
    DsTheme {
        DsTextField(
            value = "Today was a good day.",
            onValueChange = {},
            label = "Entry",
            minLines = 4,
        )
    }
}

@PreviewLightDark
@Composable
private fun DsTextFieldErrorPreview() {
    DsTheme {
        DsTextField(
            value = "not-an-email",
            onValueChange = {},
            label = "Email",
            isError = true,
            supportingText = "Enter a valid email address",
        )
    }
}
