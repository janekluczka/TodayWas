package pl.luczka.todaywas.data.local.api

import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.dao.JournalEntryDao
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.util.safeDbCall
import javax.inject.Inject

class LocalJournalDataSourceImpl @Inject constructor(
    private val dao: JournalEntryDao,
) : LocalJournalDataSource {

    override fun observeEntries(): Flow<List<JournalEntryEntity>> = dao.observeAll()

    override suspend fun getEntry(id: String): JournalEntryEntity? = dao.getById(id)

    override suspend fun insertEntry(entity: JournalEntryEntity): Result<Unit> = safeDbCall { dao.insert(entity) }

    override suspend fun updateEntry(
        id: String,
        text: String,
        updatedAt: Long,
    ): Result<JournalEntryEntity> {
        val existing = dao.getById(id) ?: return Result.failure(NoSuchElementException("Journal entry $id not found"))
        val entity = existing.copy(text = text, updatedAt = updatedAt)
        return safeDbCall { dao.update(entity) }.map { entity }
    }

    override suspend fun deleteEntry(
        id: String,
        deletedAt: Long,
    ): Result<JournalEntryEntity?> {
        val existing = dao.getById(id)
        return safeDbCall { dao.softDeleteById(id, deletedAt) }.map { existing?.copy(deletedAt = deletedAt) }
    }

    override suspend fun getAllIncludingDeleted(): List<JournalEntryEntity> = dao.getAllIncludingDeleted()

    override suspend fun applyRemoteSnapshot(toApply: List<JournalEntryEntity>) {
        dao.upsertAll(toApply)
    }

    override suspend fun purgeDeletedBefore(cutoff: Long) {
        dao.purgeDeletedBefore(cutoff)
    }

    override suspend fun clearAll(): Result<Unit> = safeDbCall { dao.clearAll() }
}
