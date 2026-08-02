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

    var updateEntryResult: Result<Unit> = Result.success(Unit)
    var updateEntryCallCount = 0
        private set
    var lastUpdatedId: Long? = null
        private set
    var lastUpdatedText: String? = null
        private set

    override fun observeEntries(): Flow<List<JournalEntry>> = entriesFlow

    override suspend fun getEntry(id: Long): JournalEntry? = entriesFlow.value.find { it.id == id }

    override suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit> {
        addEntryCallCount++
        lastSavedDate = date
        lastSavedText = text
        return addEntryResult
    }

    override suspend fun updateEntry(
        id: Long,
        text: String,
    ): Result<Unit> {
        updateEntryCallCount++
        lastUpdatedId = id
        lastUpdatedText = text
        return updateEntryResult
    }
}
