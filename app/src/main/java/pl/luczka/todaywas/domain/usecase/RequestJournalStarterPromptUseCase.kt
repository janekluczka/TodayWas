package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.model.AiPromptResult
import pl.luczka.todaywas.domain.model.JournalPromptTone
import pl.luczka.todaywas.domain.repository.AiAssistRepository

class RequestJournalStarterPromptUseCase @Inject constructor(
    private val repository: AiAssistRepository,
) {

    suspend operator fun invoke(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<AiPromptResult> = repository.generateJournalStarterPrompt(tone, thoughts)
}
