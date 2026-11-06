package pl.luczka.todaywas.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// App-specific color roles M3's ColorScheme has no slot for, picked from DsColor per light/dark theme the same way
// DsTheme picks the ColorScheme. Provided by DsTheme through a CompositionLocal (the official pattern for custom colors
// that differ between themes) and read as DsTheme.colors.xxx — components never reach into DsColor or branch on
// isSystemInDarkTheme() themselves.
@Immutable
data class DsColors(
    // Habit "done" state: its own green, separate from the teal primary, so a completed check-in reads as its own
    // semantic color rather than blending into the brand color.
    val successContainer: Color,
    val onSuccessContainer: Color,
    val contribution: DsContributionColors,
    val onContribution: DsContributionColors,
)

// One color per contribution level, NONE through LEVEL_5. A tonal ramp of the app's own teal hue, fixed per theme (not
// derived from ColorScheme) so relative intensity comparisons stay meaningful. onContribution holds the label color
// for each level: neutral10 on the light cells, neutral100 on the dark ones.
@Immutable
data class DsContributionColors(
    val none: Color,
    val level1: Color,
    val level2: Color,
    val level3: Color,
    val level4: Color,
    val level5: Color,
)

internal val LightDsColors = DsColors(
    successContainer = DsColor.green90,
    onSuccessContainer = DsColor.green40,
    contribution = DsContributionColors(
        none = DsColor.neutralVariant90,
        level1 = DsColor.teal90,
        level2 = DsColor.teal70,
        level3 = DsColor.teal60,
        level4 = DsColor.teal40,
        level5 = DsColor.teal10,
    ),
    onContribution = DsContributionColors(
        none = DsColor.neutral10,
        level1 = DsColor.neutral10,
        level2 = DsColor.neutral100,
        level3 = DsColor.neutral100,
        level4 = DsColor.neutral100,
        level5 = DsColor.neutral100,
    ),
)

internal val DarkDsColors = DsColors(
    successContainer = DsColor.green20,
    onSuccessContainer = DsColor.green80,
    contribution = DsContributionColors(
        none = DsColor.neutralVariant30,
        level1 = DsColor.teal30,
        level2 = DsColor.teal60,
        level3 = DsColor.teal70,
        level4 = DsColor.teal80,
        level5 = DsColor.teal90,
    ),
    onContribution = DsContributionColors(
        none = DsColor.neutral100,
        level1 = DsColor.neutral100,
        level2 = DsColor.neutral100,
        level3 = DsColor.neutral100,
        level4 = DsColor.neutral10,
        level5 = DsColor.neutral10,
    ),
)

internal val LocalDsColors = staticCompositionLocalOf { LightDsColors }
