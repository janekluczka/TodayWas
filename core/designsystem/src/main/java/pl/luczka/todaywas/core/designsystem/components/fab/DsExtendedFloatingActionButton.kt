package pl.luczka.todaywas.core.designsystem.components.fab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.theme.DsTheme

// Standard M3 disabled-container/content alpha (matches Button's own disabled treatment) — an
// unavailable FAB action stays visible but reads as unavailable, rather than disappearing from the
// menu entirely. That disabled container color is translucent by design (it's the same "disabled"
// tint every M3 component uses), so on its own it reads fine over the screen background but goes
// murky over busy content (list rows, cards) sitting behind the FAB menu. A same-shaped backdrop in
// the app background color sits behind the button — matchParentSize (not a modifier merged into
// the button itself) so it's guaranteed to exactly match the button's own resolved size rather than
// depending on how the button's internal Surface happens to size itself.
@Composable
fun DsExtendedFloatingActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.large
    val containerColor = if (enabled) {
        FloatingActionButtonDefaults.containerColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = if (enabled) {
        contentColorFor(containerColor)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    // A disabled control casts no shadow (matches Button's own disabled treatment) — with a
    // translucent container color, the default elevated shadow would otherwise show through as a
    // dark halo around the button's edges, on top of the opaque backdrop below.
    val elevation = if (enabled) {
        FloatingActionButtonDefaults.elevation()
    } else {
        FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        )
    }
    Box(modifier = modifier) {
        if (!enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.background),
            )
        }
        ExtendedFloatingActionButton(
            onClick = { if (enabled) onClick() },
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
        ) {
            DsText(text = text)
        }
    }
}

@PreviewLightDark
@Composable
private fun DsExtendedFloatingActionButtonPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) enabled: Boolean,
) {
    DsTheme {
        DsExtendedFloatingActionButton(
            text = "Add journal",
            onClick = {},
            enabled = enabled,
        )
    }
}
