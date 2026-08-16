package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.JournalRepository
import javax.inject.Inject

class GetJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {
    suspend operator fun invoke(id: String): JournalEntry? = repository.getEntry(id)
}
