package pl.luczka.todaywas.data.repository

import pl.luczka.todaywas.domain.model.JournalPromptTone

interface AiAssistRepository {

    suspend fun generateJournalStarterPrompt(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<String>

    suspend fun refineJournalEntry(
        text: String,
        tone: JournalPromptTone,
    ): Result<String>
}
