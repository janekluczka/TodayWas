package pl.luczka.todaywas.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Raw tonal palette — hue name + M3 tone step (0 = black, 100 = white), no semantic meaning of its
// own. Calm/cool direction: teal (primary), sage (secondary), blue (tertiary), plus red (error),
// green (habit "done" state — see below) and a cool grey-green neutral pair. Hand-tuned, first-pass
// values (not run through Material Theme Builder); revisit once visible on real screens.
//
// DsTheme.kt maps roles (primary, primaryContainer, ...) onto these swatches for
// MaterialTheme.colorScheme. Not every swatch here is used by DsTheme — this object is also where
// a component reaches for a specific fixed color it deliberately keeps outside the themed
// ColorScheme (e.g. DsContributionGrid's level colors, or the habit "done" state below).
object DsColor {

    val neutral100 = Color(0xFFFFFFFF)

    // Teal — primary
    val teal10 = Color(0xFF00201A)
    val teal20 = Color(0xFF00382E)
    val teal30 = Color(0xFF1F4F44)
    val teal40 = Color(0xFF3A6A5E)
    val teal60 = Color(0xFF5B8D7F)
    val teal70 = Color(0xFF7CAFA1)
    val teal80 = Color(0xFF9DD2C2)
    val teal90 = Color(0xFFB9ECDA)

    // Sage — secondary
    val sage10 = Color(0xFF092016)
    val sage20 = Color(0xFF1E352B)
    val sage30 = Color(0xFF344A40)
    val sage40 = Color(0xFF4C6259)
    val sage80 = Color(0xFFB2CCBE)
    val sage90 = Color(0xFFCEE9DA)

    // Blue — tertiary
    val blue10 = Color(0xFF001F2A)
    val blue20 = Color(0xFF063546)
    val blue30 = Color(0xFF244C5D)
    val blue40 = Color(0xFF3F6375)
    val blue80 = Color(0xFFA7CCE0)
    val blue90 = Color(0xFFC3E8FC)

    // Red — error (kept close to the M3 baseline red, well-tested contrast)
    val red10 = Color(0xFF410002)
    val red20 = Color(0xFF690005)
    val red30 = Color(0xFF93000A)
    val red40 = Color(0xFFBA1A1A)
    val red80 = Color(0xFFFFB4AB)
    val red90 = Color(0xFFFFDAD6)

    // Neutral — cool, faintly green-grey (pairs with teal) rather than M3's default purple-tinted
    // neutral. Used for background/surface/onBackground/onSurface/inverse* roles.
    val neutral6 = Color(0xFF0F1512)
    val neutral10 = Color(0xFF171D1A)
    val neutral20 = Color(0xFF2B322E)
    val neutral90 = Color(0xFFDEE4DF)
    val neutral95 = Color(0xFFECF2ED)
    val neutral99 = Color(0xFFF6FBF7)

    // Neutral variant — surfaceVariant/onSurfaceVariant/outline family
    val neutralVariant30 = Color(0xFF404944)
    val neutralVariant50 = Color(0xFF707974)
    val neutralVariant60 = Color(0xFF8A938D)
    val neutralVariant80 = Color(0xFFC0C9C3)
    val neutralVariant90 = Color(0xFFDBE5DF)

    // Green — habit "done" state (LogHabitCheckInsScreen). Deliberately doesn't go through
    // MaterialTheme.colorScheme/DsTheme: kept as its own hue, separate from the teal primary, so a
    // completed check-in reads as its own semantic color rather than blending into the brand color.
    val green20 = Color(0xFF1B5E20)
    val green40 = Color(0xFF2E7D32)
    val green80 = Color(0xFFA5D6A7)
    val green90 = Color(0xFFC8E6C9)
}
