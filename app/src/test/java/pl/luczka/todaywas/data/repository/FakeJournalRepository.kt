package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.LocalDate

class FakeJournalRepository(
    initialEntries: List<JournalEntry> = emptyList(),
) : JournalRepository {

    val entriesFlow = MutableStateFlow(initialEntries)

    var addEntryResult: Result<Unit> = Result.success(Unit)
    var addEntryCallCount = 0
        private set
    var lastSavedDate: LocalDate? = null
        private set
    var lastSavedText: String? = null
        private set

    override fun observeEntries(): Flow<List<JournalEntry>> = entriesFlow

    override suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit> {
        addEntryCallCount++
        lastSavedDate = date
        lastSavedText = text
        return addEntryResult
    }
}
