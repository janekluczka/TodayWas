package pl.luczka.todaywas.core.domain.repository

import pl.luczka.todaywas.core.domain.model.AiPromptResult
import pl.luczka.todaywas.core.domain.model.JournalPromptTone

interface AiAssistRepository {

    suspend fun generateJournalStarterPrompt(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<AiPromptResult>

    suspend fun refineJournalEntry(
        text: String,
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<AiPromptResult>
}
