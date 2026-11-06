package pl.luczka.todaywas.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Elevation levels for `:core:designsystem` components, named after Material 3's elevation levels (level0 = 0dp,
 * level1 = 1dp, level2 = 3dp, level3 = 6dp, level4 = 8dp, level5 = 12dp).
 *
 * Sparse like [DsSpacing]: only the levels a component sets explicitly get a token — M3 components already apply their
 * own default levels.
 */
object DsElevation {
    val level0 = 0.dp
}
