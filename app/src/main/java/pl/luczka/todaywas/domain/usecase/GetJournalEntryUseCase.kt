package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.domain.model.JournalEntry
import javax.inject.Inject

class GetJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {
    suspend operator fun invoke(id: String): JournalEntry? = repository.getEntry(id)
}
