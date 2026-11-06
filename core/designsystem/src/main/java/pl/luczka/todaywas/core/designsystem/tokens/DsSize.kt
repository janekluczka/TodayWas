package pl.luczka.todaywas.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Fixed component sizes for `:core:designsystem` components and screens.
 *
 * Unlike [DsSpacing], each token is named after the role it sizes (as Material 3's own component tokens are, e.g. a
 * button's icon size), not a scale step: sizes don't share one progression, and a role name tells a call site which
 * token is its own. Only sizes something actually uses get a token.
 */
object DsSize {
    val iconInButton = 20.dp
    val progressIndicatorInline = 20.dp

    val contributionCellSmall = 12.dp
    val contributionCellMedium = 24.dp
    val contributionCellLarge = 32.dp
    val contributionBadge = 32.dp

    val dateStripCardWidth = 56.dp
    val skeletonLineHeight = 16.dp
    val emptySectionHeight = 96.dp
}
