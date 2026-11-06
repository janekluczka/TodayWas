package pl.luczka.todaywas.core.designsystem.components.contribution

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsRadius
import pl.luczka.todaywas.core.designsystem.tokens.DsSize

private val BADGE_CORNER_RADIUS = DsRadius.contributionBadge

// A standalone, single colored cell for showing one day's value outside a grid context (e.g. a list row's trailing
// content) — reuses the exact same level->color lookup DsContributionGrid's cells use, so a badge and the real grid
// always agree on what a given level looks like. The ramp spans very light to very dark (and flips between light and
// dark theme), so each level carries its own label color in DsTheme.colors.onContribution, the way M3 pairs a container
// with its "on" color.
@Composable
fun DsContributionValueBadge(
    level: DsContributionLevel,
    valueText: String,
    modifier: Modifier = Modifier,
    size: Dp = DsSize.contributionBadge,
) {
    val backgroundColor = DsTheme.colors.contribution.of(level)
    val labelColor = DsTheme.colors.onContribution.of(level)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(BADGE_CORNER_RADIUS),
            ),
    ) {
        DsText(
            text = valueText,
            color = labelColor,
        )
    }
}

@PreviewLightDark
@Composable
private fun DsContributionValueBadgePreview() {
    DsTheme {
        DsContributionValueBadge(
            level = DsContributionLevel.LEVEL_4,
            valueText = "3",
        )
    }
}

@PreviewLightDark
@Composable
private fun DsContributionValueBadgeNotLoggedPreview() {
    DsTheme {
        DsContributionValueBadge(
            level = DsContributionLevel.NONE,
            valueText = "",
        )
    }
}
