package pl.luczka.todaywas.domain.repository

import pl.luczka.todaywas.domain.model.AiPromptResult
import pl.luczka.todaywas.domain.model.JournalPromptTone

interface AiAssistRepository {

    suspend fun generateJournalStarterPrompt(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<AiPromptResult>

    suspend fun refineJournalEntry(
        text: String,
        tone: JournalPromptTone,
    ): Result<AiPromptResult>
}
