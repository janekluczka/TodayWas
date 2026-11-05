package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.core.domain.model.AiPromptResult
import pl.luczka.todaywas.core.domain.model.JournalPromptTone
import pl.luczka.todaywas.core.domain.repository.AiAssistRepository

class RequestJournalRefinementPromptUseCase @Inject constructor(
    private val repository: AiAssistRepository,
) {

    suspend operator fun invoke(
        text: String,
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<AiPromptResult> = repository.refineJournalEntry(text, tone, thoughts)
}
