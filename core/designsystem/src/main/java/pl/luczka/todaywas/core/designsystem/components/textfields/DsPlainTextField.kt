package pl.luczka.todaywas.core.designsystem.components.textfields

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

// A borderless, containerless text field for "free writing" contexts (the journal body) -- no
// outline/underline/filled background, just text blending into the surrounding surface, closer to
// a notes app than a form field. DsTextField/DsFilledTextField are the right choice for anything
// that reads as a form (labeled fields, validation, inline errors) -- this is specifically for the
// opposite case, where a visible field boundary would work against the writing experience.
@Composable
fun DsPlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    // When true, the tappable/editable area fills the caller's full allocated bounds instead of
    // sizing to minLines — for a "the whole screen is the writing surface" field (the journal
    // body) where tapping anywhere below the last line should still place the cursor and start
    // typing. Leave false for a compact field sized to its content, like a short optional-notes
    // field inside a sheet.
    fillAvailableSpace: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val contentColor = LocalContentColor.current
    Box(modifier = modifier) {
        if (value.isEmpty() && placeholder != null) {
            DsText(
                text = placeholder,
                style = textStyle,
                color = contentColor.copy(alpha = 0.6f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            minLines = minLines,
            textStyle = textStyle.copy(color = contentColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = if (fillAvailableSpace) Modifier.fillMaxSize() else Modifier,
        )
    }
}

private class DsPlainTextFieldPreviewProvider : PreviewParameterProvider<String> {
    override val values = sequenceOf("", "Today was a good day. I went for a walk and read.")
}

@PreviewLightDark
@Composable
private fun DsPlainTextFieldPreview(
    @PreviewParameter(DsPlainTextFieldPreviewProvider::class) value: String,
) {
    DsTheme {
        DsPlainTextField(
            value = value,
            onValueChange = {},
            placeholder = "Write about your day...",
            minLines = 6,
        )
    }
}
