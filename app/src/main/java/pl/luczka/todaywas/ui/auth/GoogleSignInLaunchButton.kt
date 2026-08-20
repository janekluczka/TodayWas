package pl.luczka.todaywas.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import pl.luczka.todaywas.BuildConfig
import pl.luczka.todaywas.R

// Shared by SignInFormContent and SignUpFormContent — Google's own OAuth flow doesn't distinguish
// sign-in from sign-up (the backend creates an account transparently if one doesn't exist yet), so
// both forms drive the exact same Credential Manager flow, just with different button copy.
@Composable
fun GoogleSignInLaunchButton(
    enabled: Boolean,
    onIdTokenReceived: (String) -> Unit,
    onFailed: () -> Unit,
    text: String = stringResource(R.string.auth_form_google_cta),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLaunching by remember { mutableStateOf(false) }

    GoogleSignInButton(
        enabled = enabled && !isLaunching,
        text = text,
        onClick = {
            // Google sign-in isn't configured until GOOGLE_WEB_CLIENT_ID is set in
            // local.properties (external Google Cloud prerequisite) — fail gracefully rather
            // than letting GetGoogleIdOption throw IllegalArgumentException.
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                onFailed()
            } else {
                isLaunching = true
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
                        val result = CredentialManager
                            .create(context)
                            .getCredential(context, request)
                        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                        onIdTokenReceived(credential.idToken)
                    } catch (e: GetCredentialCancellationException) {
                        // User dismissed the system account picker — not a failure, no error to show.
                    } catch (e: GetCredentialException) {
                        onFailed()
                    } catch (e: GoogleIdTokenParsingException) {
                        onFailed()
                    } finally {
                        isLaunching = false
                    }
                }
            }
        },
        modifier = modifier,
    )
}
