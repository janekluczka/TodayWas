package pl.luczka.todaywas.data.mapper

import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import pl.luczka.todaywas.domain.model.AiAssistError
import java.io.IOException

private const val HTTP_TOO_MANY_REQUESTS = 429

// HttpRequestException (connectivity failures) and HttpRequestTimeoutException (timeouts) are
// themselves IOException subclasses, so a single IOException branch covers both. 429 (daily quota
// exceeded, see ai-proxy's increment_ai_assist_usage check) has no dedicated supabase-kt exception
// type — it falls under the generic RestException/UnknownRestException, so it's distinguished by
// statusCode instead.
fun Throwable.toAiAssistError(): AiAssistError = when (this) {
    is UnauthorizedRestException -> AiAssistError.NotSignedIn
    is BadRequestRestException -> AiAssistError.InvalidRequest
    is RestException -> if (statusCode == HTTP_TOO_MANY_REQUESTS) {
        AiAssistError.DailyLimitReached
    } else {
        AiAssistError.UpstreamFailed
    }
    is IOException -> AiAssistError.NetworkUnavailable
    else -> AiAssistError.Unknown
}
