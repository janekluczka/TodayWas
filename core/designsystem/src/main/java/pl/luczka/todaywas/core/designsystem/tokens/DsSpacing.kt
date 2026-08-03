package pl.luczka.todaywas.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Spacing scale for `:core:designsystem` components and screens, on a 4dp base unit.
 *
 * Each token is named after its multiple of the base unit x100 ([space100] = 1x base, [space200]
 * = 2x, ...) rather than a semantic size name (e.g. "small"/"medium"), so a call site never goes
 * stale if a step's underlying dp value is retuned later.
 *
 * The scale is intentionally sparse: only steps a component or screen actually needs get a token,
 * so gaps like `space500` (20dp) or `space700` (28dp) are skipped rather than filled in for
 * completeness.
 */
object DsSpacing {
    val space100 = 4.dp
    val space200 = 8.dp
    val space300 = 12.dp
    val space400 = 16.dp
    val space600 = 24.dp
    val space800 = 32.dp
    val space1000 = 40.dp
}
