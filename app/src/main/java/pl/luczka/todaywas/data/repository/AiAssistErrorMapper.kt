package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import pl.luczka.todaywas.domain.model.AiAssistError
import java.io.IOException

// HttpRequestException (connectivity failures) and HttpRequestTimeoutException (timeouts) are
// themselves IOException subclasses, so a single IOException branch covers both.
fun Throwable.toAiAssistError(): AiAssistError = when (this) {
    is UnauthorizedRestException -> AiAssistError.NotSignedIn
    is BadRequestRestException -> AiAssistError.InvalidRequest
    is RestException -> AiAssistError.UpstreamFailed
    is IOException -> AiAssistError.NetworkUnavailable
    else -> AiAssistError.Unknown
}
