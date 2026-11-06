package pl.luczka.todaywas.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Stroke widths for borders and outlines drawn by `:core:designsystem` components, plus the hairline gaps that set a
 * stroke off from what it outlines.
 *
 * Named after the role, like [DsSize]: only strokes something actually draws get a token.
 */
object DsStroke {
    val selectionRing = 2.dp
    val selectionRingGap = 1.dp
}
