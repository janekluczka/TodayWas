package pl.luczka.todaywas.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Corner radius scale for `:core:designsystem` components, named after Material 3's shape scale.
 *
 * The five scale steps carry M3's default values and back [pl.luczka.todaywas.core.designsystem.theme.DsShapes], so
 * `MaterialTheme.shapes` and a component that needs a bare radius (e.g. a per-corner shape) read from one scale. Use
 * `MaterialTheme.shapes.*` when a whole shape fits; use these only where a `Dp` radius is needed.
 *
 * Values off the M3 scale get a semantic name for the one component that uses them.
 */
object DsRadius {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 28.dp

    val contributionBadge = 6.dp
}
