package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.local.JournalEntryDao
import pl.luczka.todaywas.data.local.JournalEntryEntity
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val dao: JournalEntryDao,
) : JournalRepository {

    override fun observeEntries(): Flow<List<JournalEntry>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEntry(id: String): JournalEntry? = dao.getById(id)?.toDomain()

    override suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit> {
        val entity = JournalEntryEntity(
            id = UUID.randomUUID().toString(),
            date = date.toString(),
            text = text,
            createdAt = Instant.now().toEpochMilli(),
        )
        return safeDbCall { dao.insert(entity) }
    }

    override suspend fun updateEntry(
        id: String,
        text: String,
    ): Result<Unit> {
        val existing = dao.getById(id) ?: return Result.failure(NoSuchElementException("Journal entry $id not found"))
        val entity = existing.copy(text = text)
        return safeDbCall { dao.update(entity) }
    }
}
