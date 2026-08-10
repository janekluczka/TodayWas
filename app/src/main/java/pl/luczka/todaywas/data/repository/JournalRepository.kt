package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.LocalDate

interface JournalRepository {

    fun observeEntries(): Flow<List<JournalEntry>>

    suspend fun getEntry(id: String): JournalEntry?

    suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit>

    // Callers must check EditWindow.isEditable first - enforced by UpdateJournalEntryUseCase,
    // not here.
    suspend fun updateEntry(
        id: String,
        text: String,
    ): Result<Unit>
}
