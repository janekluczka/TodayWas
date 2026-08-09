package pl.luczka.todaywas.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import pl.luczka.todaywas.BuildConfig
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
        modifier = modifier.fillMaxWidth(),
    ) {
        DsTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = stringResource(R.string.auth_form_email_label),
            enabled = !state.isSubmitting,
            isError = state.emailError,
            supportingText = if (state.emailError) stringResource(R.string.auth_form_email_error) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        DsTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = stringResource(R.string.auth_form_password_label),
            enabled = !state.isSubmitting,
            isError = state.passwordError,
            supportingText = if (state.passwordError) stringResource(R.string.auth_form_password_error) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        DsButtonWithLoading(
            text = stringResource(R.string.auth_form_sign_in_cta),
            onClick = onSubmitClicked,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        GoogleSignInButton(
            enabled = !state.isSubmitting,
            onClick = {
                // Google sign-in isn't configured until GOOGLE_WEB_CLIENT_ID is set in
                // local.properties (external Google Cloud prerequisite) — fail gracefully
                // rather than letting GetGoogleIdOption throw IllegalArgumentException.
                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                    onGoogleSignInFailed()
                } else {
                    coroutineScope.launch {
                        try {
                            val googleIdOption = GetGoogleIdOption
                                .Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                .build()
                            val request = GetCredentialRequest
                                .Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val result = CredentialManager.create(context).getCredential(context, request)
                            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                            onGoogleIdTokenReceived(credential.idToken)
                        } catch (e: GetCredentialException) {
                            onGoogleSignInFailed()
                        } catch (e: GoogleIdTokenParsingException) {
                            onGoogleSignInFailed()
                        }
                    }
                }
            },
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
