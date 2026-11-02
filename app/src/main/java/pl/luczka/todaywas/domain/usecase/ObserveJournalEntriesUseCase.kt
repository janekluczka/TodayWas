package pl.luczka.todaywas.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.JournalRepository

class ObserveJournalEntriesUseCase @Inject constructor(
    private val repository: JournalRepository,
) {

    operator fun invoke(): Flow<List<JournalEntry>> = repository.observeEntries()
}
