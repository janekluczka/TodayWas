package pl.luczka.todaywas.core.designsystem.components

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
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun TodayWasStepper(
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
        TodayWasIconButton(
            onClick = { onValueChange(value - 1) },
            enabled = value > range.first,
        ) {
            TodayWasIcon(
                imageVector = Icons.Default.Remove,
                contentDescription = decreaseContentDescription,
            )
        }
        TodayWasText(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        TodayWasIconButton(
            onClick = { onValueChange(value + 1) },
            enabled = value < range.last,
        ) {
            TodayWasIcon(
                imageVector = Icons.Default.Add,
                contentDescription = increaseContentDescription,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TodayWasStepperPreview() {
    DesignSystemPreviewTheme {
        TodayWasStepper(
            value = 4,
            range = 2..7,
            onValueChange = {},
            decreaseContentDescription = "Decrease",
            increaseContentDescription = "Increase",
        )
    }
}
