package pl.luczka.todaywas.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.textfields.DsTextField
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

@Composable
fun SignInFormContent(
    state: SignInFormUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmitClicked: () -> Unit,
    onGoogleIdTokenReceived: (String) -> Unit,
    onGoogleSignInFailed: () -> Unit,
    onSignUpLinkClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        DsTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = stringResource(R.string.auth_form_email_label),
            enabled = !state.isSubmitting,
            isError = state.emailError,
            // Always non-null so the supporting-text row is reserved up front — toggling it
            // between null and a string (Material3's OutlinedTextField omits the row entirely
            // when null) shifted every field below it as soon as validation kicked in.
            supportingText = if (state.emailError) {
                stringResource(R.string.auth_form_email_error)
            } else {
                ""
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        DsTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = stringResource(R.string.auth_form_password_label),
            enabled = !state.isSubmitting,
            isError = state.passwordError,
            supportingText = if (state.passwordError) {
                stringResource(R.string.auth_form_password_error)
            } else {
                ""
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        DsButtonWithLoading(
            text = stringResource(R.string.auth_form_sign_in_cta),
            onClick = onSubmitClicked,
            loading = state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DsSpacing.space200),
        )
        GoogleSignInLaunchButton(
            enabled = !state.isSubmitting,
            onIdTokenReceived = onGoogleIdTokenReceived,
            onFailed = onGoogleSignInFailed,
            modifier = Modifier.fillMaxWidth(),
        )
        DsTextButton(
            text = stringResource(R.string.auth_form_sign_up_link_cta),
            onClick = onSignUpLinkClicked,
            enabled = !state.isSubmitting,
        )
    }
}

private class SignInFormUiStatePreviewProvider : PreviewParameterProvider<SignInFormUiState> {
    override val values = sequenceOf(
        SignInFormUiState(),
        SignInFormUiState(email = "not-an-email", emailError = true),
        SignInFormUiState(isSubmitting = true),
    )
}

@PreviewLightDark
@Composable
private fun SignInFormContentPreview(
    @PreviewParameter(SignInFormUiStatePreviewProvider::class) state: SignInFormUiState,
) {
    DsTheme {
        SignInFormContent(
            state = state,
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmitClicked = {},
            onGoogleIdTokenReceived = {},
            onGoogleSignInFailed = {},
            onSignUpLinkClicked = {},
        )
    }
}
