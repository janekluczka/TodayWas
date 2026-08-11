package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.AiAssistRepository
import pl.luczka.todaywas.domain.model.JournalPromptTone
import javax.inject.Inject

class RequestJournalStarterPromptUseCase @Inject constructor(
    private val repository: AiAssistRepository,
) {

    suspend operator fun invoke(
        tone: JournalPromptTone,
        thoughts: String?,
    ): Result<String> = repository.generateJournalStarterPrompt(tone, thoughts)
}
