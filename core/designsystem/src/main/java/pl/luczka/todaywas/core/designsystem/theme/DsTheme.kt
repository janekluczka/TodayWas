package pl.luczka.todaywas.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Fixed brand palette on every device — no Material You dynamic color. Dynamic color would pull
// the scheme from the user's wallpaper, making DsColor's calm/cool palette meaningless (and making
// the contribution-grid intensity comparisons in DsContributionGrid look inconsistent next to
// whatever ColorScheme.primary happened to be that day).
//
// Role → tone mapping follows the standard M3 pattern: light uses tone 40/90/10 for
// color/container/on-container, dark uses 80/30/90 — see DsColor.kt for the raw swatches.
private val DarkColorScheme = darkColorScheme(
    primary = DsColor.teal80,
    onPrimary = DsColor.teal20,
    primaryContainer = DsColor.teal30,
    onPrimaryContainer = DsColor.teal90,
    secondary = DsColor.sage80,
    onSecondary = DsColor.sage20,
    secondaryContainer = DsColor.sage30,
    onSecondaryContainer = DsColor.sage90,
    tertiary = DsColor.blue80,
    onTertiary = DsColor.blue20,
    tertiaryContainer = DsColor.blue30,
    onTertiaryContainer = DsColor.blue90,
    error = DsColor.red80,
    onError = DsColor.red20,
    errorContainer = DsColor.red30,
    onErrorContainer = DsColor.red90,
    background = DsColor.neutral6,
    onBackground = DsColor.neutral90,
    surface = DsColor.neutral6,
    onSurface = DsColor.neutral90,
    surfaceVariant = DsColor.neutralVariant30,
    onSurfaceVariant = DsColor.neutralVariant80,
    outline = DsColor.neutralVariant60,
    outlineVariant = DsColor.neutralVariant30,
    inverseSurface = DsColor.neutral90,
    inverseOnSurface = DsColor.neutral20,
    inversePrimary = DsColor.teal40,
)

private val LightColorScheme = lightColorScheme(
    primary = DsColor.teal40,
    onPrimary = DsColor.neutral100,
    primaryContainer = DsColor.teal90,
    onPrimaryContainer = DsColor.teal10,
    secondary = DsColor.sage40,
    onSecondary = DsColor.neutral100,
    secondaryContainer = DsColor.sage90,
    onSecondaryContainer = DsColor.sage10,
    tertiary = DsColor.blue40,
    onTertiary = DsColor.neutral100,
    tertiaryContainer = DsColor.blue90,
    onTertiaryContainer = DsColor.blue10,
    error = DsColor.red40,
    onError = DsColor.neutral100,
    errorContainer = DsColor.red90,
    onErrorContainer = DsColor.red10,
    background = DsColor.neutral99,
    onBackground = DsColor.neutral10,
    surface = DsColor.neutral99,
    onSurface = DsColor.neutral10,
    surfaceVariant = DsColor.neutralVariant90,
    onSurfaceVariant = DsColor.neutralVariant30,
    outline = DsColor.neutralVariant50,
    outlineVariant = DsColor.neutralVariant80,
    inverseSurface = DsColor.neutral20,
    inverseOnSurface = DsColor.neutral95,
    inversePrimary = DsColor.teal80,
)

@Composable
fun DsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DsTypography,
        content = content,
    )
}
