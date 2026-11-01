package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.JournalRepository

class GetJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {
    suspend operator fun invoke(id: String): JournalEntry? = repository.getEntry(id)
}
