package pl.luczka.todaywas.domain.aiassist.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.aiassist.model.AiPromptResult
import pl.luczka.todaywas.domain.aiassist.model.JournalPromptTone
import pl.luczka.todaywas.domain.aiassist.repository.AiAssistRepository

class RequestJournalStarterPromptUseCase @Inject constructor(
    private val repository: AiAssistRepository,
) {

    suspend operator fun invoke(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<AiPromptResult> = repository.generateJournalStarterPrompt(tone, thoughts)
}
