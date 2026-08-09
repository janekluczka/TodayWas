package pl.luczka.todaywas.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.textfields.DsTextField
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

@Composable
fun SignUpFormContent(
    state: SignUpFormUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRepeatPasswordChanged: (String) -> Unit,
    onSubmitClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        DsTextField(
            value = state.repeatPassword,
            onValueChange = onRepeatPasswordChanged,
            label = stringResource(R.string.auth_form_repeat_password_label),
            enabled = !state.isSubmitting,
            isError = state.repeatPasswordError,
            supportingText = if (state.repeatPasswordError) {
                stringResource(R.string.auth_form_repeat_password_error)
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        DsButtonWithLoading(
            text = stringResource(R.string.auth_form_sign_up_cta),
            onClick = onSubmitClicked,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private class SignUpFormUiStatePreviewProvider : PreviewParameterProvider<SignUpFormUiState> {
    override val values = sequenceOf(
        SignUpFormUiState(),
        SignUpFormUiState(
            email = "not-an-email",
            emailError = true,
            repeatPassword = "mismatch",
            repeatPasswordError = true,
        ),
        SignUpFormUiState(isSubmitting = true),
    )
}

@PreviewLightDark
@Composable
private fun SignUpFormContentPreview(
    @PreviewParameter(SignUpFormUiStatePreviewProvider::class) state: SignUpFormUiState,
) {
    DsTheme {
        SignUpFormContent(
            state = state,
            onEmailChanged = {},
            onPasswordChanged = {},
            onRepeatPasswordChanged = {},
            onSubmitClicked = {},
        )
    }
}
