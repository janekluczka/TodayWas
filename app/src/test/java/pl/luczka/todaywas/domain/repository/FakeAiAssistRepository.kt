package pl.luczka.todaywas.domain.repository

import pl.luczka.todaywas.domain.model.AiPromptResult
import pl.luczka.todaywas.domain.model.JournalPromptTone

class FakeAiAssistRepository : AiAssistRepository {

    var generateResult: Result<AiPromptResult> =
        Result.success(AiPromptResult(text = "Generated prompt.", remainingToday = 9))

    var generateCallCount = 0
        private set

    var lastTone: JournalPromptTone? = null
        private set

    var lastThoughts: String? = null
        private set

    var refineResult: Result<AiPromptResult> =
        Result.success(AiPromptResult(text = "Refined text.", remainingToday = 9))

    var refineCallCount = 0
        private set

    var lastRefineText: String? = null
        private set

    var lastRefineTone: JournalPromptTone? = null
        private set

    override suspend fun generateJournalStarterPrompt(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<AiPromptResult> {
        generateCallCount++
        lastTone = tone
        lastThoughts = thoughts
        return generateResult
    }

    override suspend fun refineJournalEntry(
        text: String,
        tone: JournalPromptTone,
    ): Result<AiPromptResult> {
        refineCallCount++
        lastRefineText = text
        lastRefineTone = tone
        return refineResult
    }
}
