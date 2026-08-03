package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun DsCardPreview() {
    DesignSystemPreviewTheme {
        DsCard {
            DsText(
                text = "Card content",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
