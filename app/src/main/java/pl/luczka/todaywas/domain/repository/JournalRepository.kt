package pl.luczka.todaywas.domain.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.JournalEntry

interface JournalRepository : Syncable {

    fun observeEntries(): Flow<List<JournalEntry>>

    suspend fun getEntry(id: String): JournalEntry?

    suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit>

    // Callers must check EditWindow.isEditable against the stored entry first - enforced by
    // UpdateJournalEntryUseCase, not here.
    suspend fun updateEntry(
        id: String,
        text: String,
    ): Result<Unit>

    suspend fun deleteEntry(id: String): Result<Unit>
}
