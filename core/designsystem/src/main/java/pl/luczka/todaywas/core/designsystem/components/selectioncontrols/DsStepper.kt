package pl.luczka.todaywas.core.designsystem.components.selectioncontrols

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsStepper(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    decreaseContentDescription: String? = null,
    increaseContentDescription: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        DsIconButton(
            onClick = { onValueChange(value - 1) },
            enabled = value > range.first,
        ) {
            DsIcon(
                imageVector = Icons.Default.Remove,
                contentDescription = decreaseContentDescription,
            )
        }
        DsText(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        DsIconButton(
            onClick = { onValueChange(value + 1) },
            enabled = value < range.last,
        ) {
            DsIcon(
                imageVector = Icons.Default.Add,
                contentDescription = increaseContentDescription,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DsStepperPreview() {
    DesignSystemPreviewTheme {
        DsStepper(
            value = 4,
            range = 2..7,
            onValueChange = {},
            decreaseContentDescription = "Decrease",
            increaseContentDescription = "Increase",
        )
    }
}
