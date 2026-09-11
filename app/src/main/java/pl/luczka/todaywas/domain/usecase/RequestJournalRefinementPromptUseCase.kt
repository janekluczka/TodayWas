package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.model.AiPromptResult
import pl.luczka.todaywas.domain.model.JournalPromptTone
import pl.luczka.todaywas.domain.repository.AiAssistRepository
import javax.inject.Inject

class RequestJournalRefinementPromptUseCase @Inject constructor(
    private val repository: AiAssistRepository,
) {

    suspend operator fun invoke(
        text: String,
        tone: JournalPromptTone,
    ): Result<AiPromptResult> = repository.refineJournalEntry(text, tone)
}
