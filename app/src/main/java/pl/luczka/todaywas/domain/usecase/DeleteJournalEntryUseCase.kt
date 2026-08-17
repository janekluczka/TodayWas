package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.repository.JournalRepository
import javax.inject.Inject

class DeleteJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteEntry(id)
}
