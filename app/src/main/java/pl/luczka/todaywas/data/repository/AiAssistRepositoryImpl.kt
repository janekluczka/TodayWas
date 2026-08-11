package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import pl.luczka.todaywas.domain.model.AiAssistException
import pl.luczka.todaywas.domain.model.JournalPromptTone
import javax.inject.Inject

private const val FUNCTION_NAME = "ai-proxy"

class AiAssistRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : AiAssistRepository {

    override suspend fun generateJournalStarterPrompt(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<String> = try {
        val response = supabase.functions.invoke(
            function = FUNCTION_NAME,
            body = AiPromptRequestDto(tone = tone.level, thoughts = thoughts),
            headers = Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
        )
        Result.success(response.body<AiPromptResponseDto>().text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AiAssistException(e.toAiAssistError()))
    }
}
