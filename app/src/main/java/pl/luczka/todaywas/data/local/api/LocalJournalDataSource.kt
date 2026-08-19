package pl.luczka.todaywas.data.local.api

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity

interface LocalJournalDataSource {

    fun observeEntries(): Flow<List<JournalEntryEntity>>

    suspend fun getEntry(id: String): JournalEntryEntity?

    suspend fun insertEntry(entity: JournalEntryEntity): Result<Unit>

    suspend fun updateEntry(
        id: String,
        text: String,
        updatedAt: Long,
    ): Result<JournalEntryEntity>

    suspend fun deleteEntry(
        id: String,
        deletedAt: Long,
    ): Result<JournalEntryEntity?>

    suspend fun getAllIncludingDeleted(): List<JournalEntryEntity>

    suspend fun applyRemoteSnapshot(toApply: List<JournalEntryEntity>)

    suspend fun purgeDeletedBefore(cutoff: Long)

    suspend fun clearAll(): Result<Unit>
}
