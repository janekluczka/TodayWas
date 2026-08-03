package pl.luczka.todaywas.core.designsystem.components.cards

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
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

enum class DsCardVariant {
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
fun DsSelectableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: DsCardVariant = DsCardVariant.NEUTRAL,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val containerColor: Color
    val contentColor: Color
    when {
        !enabled -> {
            val isSelectedVariant = variant != DsCardVariant.NEUTRAL
            containerColor = if (isSelectedVariant) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        variant == DsCardVariant.PRIMARY -> {
            containerColor = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimary
        }
        variant == DsCardVariant.SECONDARY -> {
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
        variant == DsCardVariant.TERTIARY -> {
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

private class DsCardVariantPreviewProvider : PreviewParameterProvider<DsCardVariant> {
    override val values = DsCardVariant.entries.asSequence()
}

@PreviewLightDark
@Composable
private fun DsSelectableCardPreview(
    @PreviewParameter(DsCardVariantPreviewProvider::class) variant: DsCardVariant,
) {
    DesignSystemPreviewTheme {
        DsSelectableCard(
            onClick = {},
            variant = variant,
        ) {
            DsText(text = variant.name)
        }
    }
}

@PreviewLightDark
@Composable
private fun DsSelectableCardDisabledPreview() {
    DesignSystemPreviewTheme {
        DsSelectableCard(
            onClick = {},
            enabled = false,
            variant = DsCardVariant.PRIMARY,
        ) {
            DsText(text = "Disabled")
        }
    }
}
