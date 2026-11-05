package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import pl.luczka.todaywas.core.domain.model.JournalEntry
import pl.luczka.todaywas.core.domain.repository.JournalRepository

class GetJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {
    suspend operator fun invoke(id: String): JournalEntry? = repository.getEntry(id)
}
