package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.domain.model.AiAssistError
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState

fun AiAssistError.toUiState(): AiAssistErrorUiState = when (this) {
    AiAssistError.InvalidRequest -> AiAssistErrorUiState.INVALID_REQUEST
    AiAssistError.UpstreamFailed -> AiAssistErrorUiState.UPSTREAM_FAILED
    AiAssistError.NotSignedIn -> AiAssistErrorUiState.NOT_SIGNED_IN
    AiAssistError.NetworkUnavailable -> AiAssistErrorUiState.NETWORK_UNAVAILABLE
    AiAssistError.Unknown -> AiAssistErrorUiState.UNKNOWN
}
