package pl.luczka.todaywas.domain.journal.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.journal.model.JournalEntry
import pl.luczka.todaywas.domain.journal.repository.JournalRepository

class ObserveJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
) {

    operator fun invoke(id: String): Flow<JournalEntry?> = repository.observeEntry(id)
}
