package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.domain.model.AiAssistError

fun AiAssistError.toUiState(): AiAssistErrorUiState = when (this) {
    AiAssistError.InvalidRequest -> AiAssistErrorUiState.INVALID_REQUEST
    AiAssistError.UpstreamFailed -> AiAssistErrorUiState.UPSTREAM_FAILED
    AiAssistError.NotSignedIn -> AiAssistErrorUiState.NOT_SIGNED_IN
    AiAssistError.NetworkUnavailable -> AiAssistErrorUiState.NETWORK_UNAVAILABLE
    AiAssistError.Unknown -> AiAssistErrorUiState.UNKNOWN
}
