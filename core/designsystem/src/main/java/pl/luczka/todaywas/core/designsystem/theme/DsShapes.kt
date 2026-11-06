package pl.luczka.todaywas.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import pl.luczka.todaywas.core.designsystem.tokens.DsRadius

// M3's shape scale built from DsRadius, so M3 components (via MaterialTheme.shapes) and Ds components that need a bare
// radius share one source. The values equal M3's defaults; passing them explicitly just makes DsRadius the owner.
internal val DsShapes = Shapes(
    extraSmall = RoundedCornerShape(DsRadius.extraSmall),
    small = RoundedCornerShape(DsRadius.small),
    medium = RoundedCornerShape(DsRadius.medium),
    large = RoundedCornerShape(DsRadius.large),
    extraLarge = RoundedCornerShape(DsRadius.extraLarge),
)
