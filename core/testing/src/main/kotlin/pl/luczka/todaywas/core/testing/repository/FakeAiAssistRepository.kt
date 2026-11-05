package pl.luczka.todaywas.core.testing.repository

import pl.luczka.todaywas.core.domain.model.AiPromptResult
import pl.luczka.todaywas.core.domain.model.JournalPromptTone
import pl.luczka.todaywas.core.domain.repository.AiAssistRepository

class FakeAiAssistRepository : AiAssistRepository {

    var generateResult: Result<AiPromptResult> =
        Result.success(
            AiPromptResult(
                text = "Generated prompt.",
                remainingToday = 9,
            ),
        )

    var generateCallCount = 0
        private set

    var lastTone: JournalPromptTone? = null
        private set

    var lastThoughts: String? = null
        private set

    var refineResult: Result<AiPromptResult> =
        Result.success(
            AiPromptResult(
                text = "Refined text.",
                remainingToday = 9,
            ),
        )

    var refineCallCount = 0
        private set

    var lastRefineText: String? = null
        private set

    var lastRefineTone: JournalPromptTone? = null
        private set

    var lastRefineThoughts: String? = null
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
        thoughts: String?,
    ): Result<AiPromptResult> {
        refineCallCount++
        lastRefineText = text
        lastRefineTone = tone
        lastRefineThoughts = thoughts
        return refineResult
    }
}
