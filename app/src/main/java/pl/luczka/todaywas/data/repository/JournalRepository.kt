package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.LocalDate

interface JournalRepository {

    fun observeEntries(): Flow<List<JournalEntry>>

    suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit>
}
