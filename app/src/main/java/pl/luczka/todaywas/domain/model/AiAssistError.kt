package pl.luczka.todaywas.domain.model

sealed interface AiAssistError {

    data object InvalidRequest : AiAssistError

    data object UpstreamFailed : AiAssistError

    data object NotSignedIn : AiAssistError

    data object DailyLimitReached : AiAssistError

    data object NetworkUnavailable : AiAssistError

    data object Unknown : AiAssistError
}

class AiAssistException(
    val error: AiAssistError,
) : Exception()
