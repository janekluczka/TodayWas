package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.encode
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import pl.luczka.todaywas.data.mapper.toAiAssistError
import pl.luczka.todaywas.data.remote.dto.AiPromptRequestDto
import pl.luczka.todaywas.data.remote.dto.AiPromptResponseDto
import pl.luczka.todaywas.domain.model.AiAssistException
import pl.luczka.todaywas.domain.model.JournalPromptTone
import pl.luczka.todaywas.domain.repository.AiAssistRepository
import javax.inject.Inject

private const val FUNCTION_NAME = "ai-proxy"

// The ai-proxy function's upstream OpenRouter free-tier model plus Deno cold start regularly
// takes 10-20s (observed via get_logs(service: "edge-function") during manual verification) —
// well past supabase-kt's default ~10s client timeout, which caused a real 200 response to be
// misreported as an error. Both requestTimeoutMillis AND socketTimeoutMillis must be raised: the
// underlying engine's socket-read timeout is a separate setting from Ktor's request timeout, and
// leaving it at its default throws a socket-timeout HttpRequestException well before this
// duration elapses even with requestTimeoutMillis alone raised. Scoped to this call only, not the
// whole SupabaseClient, so unrelated Auth/Postgrest calls keep failing fast when genuinely offline.
private const val FUNCTION_TIMEOUT_MS = 30_000L

class AiAssistRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : AiAssistRepository {

    override suspend fun generateJournalStarterPrompt(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<String> = invokeAiProxy(AiPromptRequestDto(tone = tone.level, thoughts = thoughts))

    override suspend fun refineJournalEntry(
        text: String,
        tone: JournalPromptTone,
    ): Result<String> = invokeAiProxy(AiPromptRequestDto(tone = tone.level, text = text))

    private suspend fun invokeAiProxy(request: AiPromptRequestDto): Result<String> = try {
        val response = supabase.functions.invoke(function = FUNCTION_NAME) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(supabase.functions.serializer.encode(request))
            timeout {
                requestTimeoutMillis = FUNCTION_TIMEOUT_MS
                socketTimeoutMillis = FUNCTION_TIMEOUT_MS
            }
        }
        Result.success(response.body<AiPromptResponseDto>().text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AiAssistException(e.toAiAssistError()))
    }
}
