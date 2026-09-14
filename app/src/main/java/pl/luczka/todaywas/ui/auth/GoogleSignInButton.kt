package pl.luczka.todaywas.ui.auth

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.progress.DsLoadingIndicator
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

// A native-feeling outlined button (matches the app's own Material3 button shape/theme rather
// than Google's fixed web button styling) with a monochrome "G" mark (res/drawable/ic_google_logo)
// that inherits the button's content color, so it sits naturally alongside the app's other
// buttons instead of reading as an embedded web widget.
//
// Shows a spinner (same pattern as DsButtonWithLoading) rather than just disabling while
// launching: the Credential Manager system sheet can take a second or more to slide up, and with
// no loading feedback the button reads as broken/unresponsive during that gap.
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    text: String = stringResource(R.string.auth_form_google_cta),
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.animateContentSize(),
    ) {
        if (loading) {
            DsLoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(20.dp),
            )
        } else {
            DsIcon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_google_logo),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(DsSpacing.space200))
            DsText(text = text)
        }
    }
}

private class GoogleSignInButtonPreviewStateProvider :
    PreviewParameterProvider<Pair<Boolean, Boolean>> {
    override val values = sequenceOf(
        true to false,
        false to false,
        true to true,
    )
}

@PreviewLightDark
@Composable
private fun GoogleSignInButtonPreview(
    @PreviewParameter(GoogleSignInButtonPreviewStateProvider::class) state: Pair<Boolean, Boolean>,
) {
    val (enabled, loading) = state
    DsTheme {
        GoogleSignInButton(
            onClick = {},
            enabled = enabled,
            loading = loading,
        )
    }
}
