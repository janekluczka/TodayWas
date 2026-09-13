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
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

private val BADGE_SIZE = 32.dp
private val BADGE_CORNER_RADIUS = 6.dp

// A standalone, single colored cell for showing one day's value outside a grid context (e.g. a
// list row's trailing content) — reuses the exact same level->color lookup DsContributionGrid's
// cells use, so a badge and the real grid always agree on what a given level looks like.
@Composable
fun DsContributionValueBadge(
    level: DsContributionLevel,
    valueText: String,
    modifier: Modifier = Modifier,
    size: Dp = BADGE_SIZE,
) {
    val levelColors = contributionLevelColors()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(
                color = levelColors.getValue(level),
                shape = RoundedCornerShape(BADGE_CORNER_RADIUS),
            ),
    ) {
        DsText(text = valueText)
    }
}

@PreviewLightDark
@Composable
private fun DsContributionValueBadgePreview() {
    DsTheme {
        DsContributionValueBadge(level = DsContributionLevel.LEVEL_4, valueText = "3")
    }
}

@PreviewLightDark
@Composable
private fun DsContributionValueBadgeNotLoggedPreview() {
    DsTheme {
        DsContributionValueBadge(level = DsContributionLevel.NONE, valueText = "")
    }
}
