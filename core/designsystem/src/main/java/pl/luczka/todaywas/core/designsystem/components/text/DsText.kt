package pl.luczka.todaywas.core.designsystem.components.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.TextUnit
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

@Composable
fun DsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsTextPreview() {
    DsTheme {
        DsText(text = "What would you like to focus on?")
    }
}
