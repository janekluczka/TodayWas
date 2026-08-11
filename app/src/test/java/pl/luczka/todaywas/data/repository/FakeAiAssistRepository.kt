package pl.luczka.todaywas.data.repository

import pl.luczka.todaywas.domain.model.JournalPromptTone

class FakeAiAssistRepository : AiAssistRepository {

    var generateResult: Result<String> = Result.success("Generated prompt.")

    var generateCallCount = 0
        private set

    var lastTone: JournalPromptTone? = null
        private set

    var lastThoughts: String? = null
        private set

    var refineResult: Result<String> = Result.success("Refined text.")

    var refineCallCount = 0
        private set

    var lastRefineText: String? = null
        private set

    var lastRefineTone: JournalPromptTone? = null
        private set

    override suspend fun generateJournalStarterPrompt(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<String> {
        generateCallCount++
        lastTone = tone
        lastThoughts = thoughts
        return generateResult
    }

    override suspend fun refineJournalEntry(
        text: String,
        tone: JournalPromptTone,
    ): Result<String> {
        refineCallCount++
        lastRefineText = text
        lastRefineTone = tone
        return refineResult
    }
}
