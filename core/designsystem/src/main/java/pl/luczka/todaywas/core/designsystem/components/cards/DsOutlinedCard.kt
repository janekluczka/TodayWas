package pl.luczka.todaywas.core.designsystem.components.cards

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@Composable
fun DsOutlinedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun DsOutlinedCardPreview() {
    DesignSystemPreviewTheme {
        DsOutlinedCard {
            DsText(
                text = "Outlined card content",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
