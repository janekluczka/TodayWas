package pl.luczka.todaywas.feature.journal.impl.mapper

import pl.luczka.todaywas.core.domain.model.AiAssistError
import pl.luczka.todaywas.feature.journal.impl.model.AiAssistErrorUiState

fun AiAssistError.toUiState(): AiAssistErrorUiState = when (this) {
    AiAssistError.InvalidRequest -> AiAssistErrorUiState.INVALID_REQUEST
    AiAssistError.UpstreamFailed -> AiAssistErrorUiState.UPSTREAM_FAILED
    AiAssistError.NotSignedIn -> AiAssistErrorUiState.NOT_SIGNED_IN
    AiAssistError.DailyLimitReached -> AiAssistErrorUiState.DAILY_LIMIT_REACHED
    AiAssistError.NetworkUnavailable -> AiAssistErrorUiState.NETWORK_UNAVAILABLE
    AiAssistError.Unknown -> AiAssistErrorUiState.UNKNOWN
}
