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
import pl.luczka.todaywas.ui.model.AuthErrorUiState

@Composable
fun AuthFormContent(
    state: AuthFormUiState,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onModeToggled: () -> Unit,
    onSubmitClicked: () -> Unit,
    onGoogleIdTokenReceived: (String) -> Unit,
    onGoogleSignInFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (state.mode == AuthFormMode.SIGN_UP) {
            DsTextField(
                value = state.firstName,
                onValueChange = onFirstNameChanged,
                label = stringResource(R.string.auth_form_first_name_label),
                enabled = !state.isSubmitting,
                isError = state.firstNameError,
                supportingText = if (state.firstNameError) stringResource(R.string.auth_form_name_error) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            DsTextField(
                value = state.lastName,
                onValueChange = onLastNameChanged,
                label = stringResource(R.string.auth_form_last_name_label),
                enabled = !state.isSubmitting,
                isError = state.lastNameError,
                supportingText = if (state.lastNameError) stringResource(R.string.auth_form_name_error) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            text = submitLabel(state.mode),
            onClick = onSubmitClicked,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        DsTextButton(
            text = toggleLabel(state.mode),
            onClick = onModeToggled,
            enabled = !state.isSubmitting,
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
    }
}

@Composable
private fun submitLabel(mode: AuthFormMode): String = when (mode) {
    AuthFormMode.SIGN_UP -> stringResource(R.string.auth_form_sign_up_cta)
    AuthFormMode.SIGN_IN -> stringResource(R.string.auth_form_sign_in_cta)
}

@Composable
private fun toggleLabel(mode: AuthFormMode): String = when (mode) {
    AuthFormMode.SIGN_UP -> stringResource(R.string.auth_form_toggle_to_sign_in)
    AuthFormMode.SIGN_IN -> stringResource(R.string.auth_form_toggle_to_sign_up)
}

// Shared with any other screen hosting AuthFormContent (e.g. onboarding) so the same
// AuthErrorUiState -> copy mapping isn't duplicated per screen.
@Composable
fun AuthErrorUiState.message(): String = when (this) {
    AuthErrorUiState.EMAIL_ALREADY_REGISTERED -> stringResource(R.string.auth_error_email_already_registered)
    AuthErrorUiState.INVALID_CREDENTIALS -> stringResource(R.string.auth_error_invalid_credentials)
    AuthErrorUiState.WEAK_PASSWORD -> stringResource(R.string.auth_error_weak_password)
    AuthErrorUiState.NETWORK_UNAVAILABLE -> stringResource(R.string.auth_error_network_unavailable)
    AuthErrorUiState.UNKNOWN -> stringResource(R.string.auth_error_unknown)
}

private class AuthFormUiStatePreviewProvider : PreviewParameterProvider<AuthFormUiState> {
    override val values = sequenceOf(
        AuthFormUiState(mode = AuthFormMode.SIGN_UP),
        AuthFormUiState(
            mode = AuthFormMode.SIGN_IN,
            email = "not-an-email",
            emailError = true,
        ),
        AuthFormUiState(mode = AuthFormMode.SIGN_UP, isSubmitting = true),
    )
}

@PreviewLightDark
@Composable
private fun AuthFormContentPreview(
    @PreviewParameter(AuthFormUiStatePreviewProvider::class) state: AuthFormUiState,
) {
    DsTheme {
        AuthFormContent(
            state = state,
            onFirstNameChanged = {},
            onLastNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onModeToggled = {},
            onSubmitClicked = {},
            onGoogleIdTokenReceived = {},
            onGoogleSignInFailed = {},
        )
    }
}
