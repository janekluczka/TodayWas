package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.luczka.todaywas.data.local.JournalEntryDao
import pl.luczka.todaywas.data.local.JournalEntryEntity
import pl.luczka.todaywas.di.ApplicationScope
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val dao: JournalEntryDao,
    private val remoteDataSource: RemoteJournalDataSource,
    private val authRepository: AuthRepository,
    @ApplicationScope private val syncScope: CoroutineScope,
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
        val result = safeDbCall { dao.insert(entity) }
        if (result.isSuccess) pushInBackground(entity)
        return result
    }

    override suspend fun updateEntry(
        id: String,
        text: String,
    ): Result<Unit> {
        val existing = dao.getById(id) ?: return Result.failure(NoSuchElementException("Journal entry $id not found"))
        val entity = existing.copy(text = text)
        val result = safeDbCall { dao.update(entity) }
        if (result.isSuccess) pushInBackground(entity)
        return result
    }

    override suspend fun syncWithRemote(): Result<Unit> {
        val userId = authRepository.currentUserId() ?: return Result.success(Unit)
        return remoteCall {
            val local = dao.getAll()
            remoteDataSource.upsert(local.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()
            val remote = remoteDataSource.fetchAll(userId).getOrThrow()
            remote.forEach { dao.upsert(it.toEntity()) }
        }
    }

    override suspend fun clearLocal(): Result<Unit> = safeDbCall { dao.clearAll() }

    // Best-effort - failures are silently swallowed since the local write already succeeded;
    // the next successful write (or the next sync pass) naturally retries via upsert.
    private fun pushInBackground(entity: JournalEntryEntity) {
        val userId = authRepository.currentUserId() ?: return
        syncScope.launch {
            runCatching { remoteDataSource.upsert(listOf(entity.toDomain().toRemoteDto(userId))) }
        }
    }
}
