package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.repository.JournalRepository

class DeleteJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteEntry(id)
}
