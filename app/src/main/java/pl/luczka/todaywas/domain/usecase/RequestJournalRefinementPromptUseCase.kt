package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.AiAssistRepository
import pl.luczka.todaywas.domain.model.JournalPromptTone
import javax.inject.Inject

class RequestJournalRefinementPromptUseCase @Inject constructor(
    private val repository: AiAssistRepository,
) {

    suspend operator fun invoke(
        text: String,
        tone: JournalPromptTone,
    ): Result<String> = repository.refineJournalEntry(text, tone)
}
