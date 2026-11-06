package pl.luczka.todaywas.core.designsystem.tokens

/**
 * Opacities applied to a theme color (`MaterialTheme.colorScheme.onSurface.copy(alpha = DsAlpha.DISABLED_CONTENT)`).
 *
 * The disabled pair carries Material 3's disabled-state values (container 12%, content 38%); the others are named by
 * the role they fade. `const` floats, so they follow Kotlin's constant naming rather than the `Dp` tokens' camelCase.
 * Only opacities something actually uses get a token.
 */
object DsAlpha {
    const val DISABLED_CONTAINER = 0.12f
    const val DISABLED_CONTENT = 0.38f
    const val SKELETON = 0.24f
    const val PLACEHOLDER = 0.6f
}
