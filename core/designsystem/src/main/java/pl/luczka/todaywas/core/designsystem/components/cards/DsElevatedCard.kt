package pl.luczka.todaywas.core.designsystem.components.cards

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsElevatedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun DsElevatedCardPreview() {
    DesignSystemPreviewTheme {
        DsElevatedCard {
            DsText(
                text = "Elevated card content",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
