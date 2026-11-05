package pl.luczka.todaywas.feature.journal.impl.model

internal enum class AiAssistErrorUiState {
    INVALID_REQUEST,
    UPSTREAM_FAILED,
    NOT_SIGNED_IN,
    DAILY_LIMIT_REACHED,
    NETWORK_UNAVAILABLE,
    UNKNOWN,
}
