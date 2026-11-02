package pl.luczka.todaywas.ui.model

enum class AuthErrorUiState {
    EMAIL_ALREADY_REGISTERED,
    INVALID_CREDENTIALS,
    WEAK_PASSWORD,
    NETWORK_UNAVAILABLE,

    // Google sign-in found no Google account on the device (Credential Manager NoCredentialException).
    NO_GOOGLE_ACCOUNT,
    UNKNOWN,
}
