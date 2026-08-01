package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

enum class TodayWasCardVariant {
    NEUTRAL,
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

// A shared clickable/selectable card for small repeated-choice UI (segmented rows, date strips,
// and similar pickers) so they all render through one place and stay visually consistent.
// Disabled colors always follow Material3's standard disabled-content convention (onSurface at
// 12%/38% alpha) regardless of `variant`, matching how M3's own chips/buttons render disabled
// state — a faint container tint only when the disabled item is also the selected one.
@Composable
fun TodayWasSelectableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: TodayWasCardVariant = TodayWasCardVariant.NEUTRAL,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val containerColor: Color
    val contentColor: Color
    when {
        !enabled -> {
            val isSelectedVariant = variant != TodayWasCardVariant.NEUTRAL
            containerColor = if (isSelectedVariant) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        variant == TodayWasCardVariant.PRIMARY -> {
            containerColor = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimary
        }
        variant == TodayWasCardVariant.SECONDARY -> {
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        variant == TodayWasCardVariant.TERTIARY -> {
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            content = content,
        )
    }
}

private class TodayWasCardVariantPreviewProvider : PreviewParameterProvider<TodayWasCardVariant> {
    override val values = TodayWasCardVariant.entries.asSequence()
}

@PreviewLightDark
@Composable
private fun TodayWasSelectableCardPreview(
    @PreviewParameter(TodayWasCardVariantPreviewProvider::class) variant: TodayWasCardVariant,
) {
    DesignSystemPreviewTheme {
        TodayWasSelectableCard(
            onClick = {},
            variant = variant,
        ) {
            TodayWasText(text = variant.name)
        }
    }
}

@PreviewLightDark
@Composable
private fun TodayWasSelectableCardDisabledPreview() {
    DesignSystemPreviewTheme {
        TodayWasSelectableCard(
            onClick = {},
            enabled = false,
            variant = TodayWasCardVariant.PRIMARY,
        ) {
            TodayWasText(text = "Disabled")
        }
    }
}
