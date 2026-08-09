package pl.luczka.todaywas.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

// A native-feeling outlined button (matches the app's own Material3 button shape/theme rather
// than Google's fixed web button styling) with a monochrome "G" mark (res/drawable/ic_google_logo)
// that inherits the button's content color, so it sits naturally alongside the app's other
// buttons instead of reading as an embedded web widget.
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = stringResource(R.string.auth_form_google_cta),
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        DsIcon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_google_logo),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(DsSpacing.space200))
        DsText(text = text)
    }
}

private class GoogleSignInButtonPreviewProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true, false)
}

@PreviewLightDark
@Composable
private fun GoogleSignInButtonPreview(
    @PreviewParameter(GoogleSignInButtonPreviewProvider::class) enabled: Boolean,
) {
    DsTheme {
        GoogleSignInButton(
            onClick = {},
            enabled = enabled,
        )
    }
}
