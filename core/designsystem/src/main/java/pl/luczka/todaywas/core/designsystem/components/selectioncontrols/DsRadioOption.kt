package pl.luczka.todaywas.core.designsystem.components.selectioncontrols

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsRadioOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
            ).padding(vertical = 8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        DsText(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun DsRadioOptionPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) selected: Boolean,
) {
    DesignSystemPreviewTheme {
        DsRadioOption(
            text = "Journaling",
            selected = selected,
            onClick = {},
        )
    }
}
