package pl.luczka.todaywas.domain.aiassist.repository

import pl.luczka.todaywas.domain.aiassist.model.AiPromptResult
import pl.luczka.todaywas.domain.aiassist.model.JournalPromptTone

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
