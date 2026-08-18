package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.luczka.todaywas.data.local.dao.JournalEntryDao
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.mapper.toDomain
import pl.luczka.todaywas.data.mapper.toEntity
import pl.luczka.todaywas.data.mapper.toRemoteDto
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSource
import pl.luczka.todaywas.data.util.remoteCall
import pl.luczka.todaywas.data.util.safeDbCall
import pl.luczka.todaywas.di.ApplicationScope
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.JournalRepository
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

    // Guards against a bulk syncWithRemote() push-then-pull racing an individual
    // pushInBackground() for the same row: without this, a syncWithRemote() call that snapshot
    // a row before a concurrent edit can push/pull that stale snapshot back over the edit.
    private val syncMutex = Mutex()

    override fun observeEntries(): Flow<List<JournalEntry>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEntry(id: String): JournalEntry? = dao.getById(id)?.toDomain()

    override suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit> {
        val now = Instant.now().toEpochMilli()
        val entity = JournalEntryEntity(
            id = UUID.randomUUID().toString(),
            date = date.toString(),
            text = text,
            createdAt = now,
            updatedAt = now,
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

    override suspend fun deleteEntry(id: String): Result<Unit> {
        val result = safeDbCall { dao.softDeleteById(id, Instant.now().toEpochMilli()) }
        if (result.isSuccess) pushDeleteInBackground(id)
        return result
    }

    override suspend fun syncWithRemote(): Result<Unit> {
        val userId = authRepository.currentUserId() ?: return Result.success(Unit)
        return syncMutex.withLock {
            remoteCall {
                val local = dao.getAll()
                remoteDataSource.upsert(local.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()
                val remote = remoteDataSource.fetchAll(userId).getOrThrow()
                remote.forEach { dao.upsert(it.toEntity()) }
            }
        }
    }

    override suspend fun clearLocal(): Result<Unit> = safeDbCall { dao.clearAll() }

    // Best-effort - failures are silently swallowed since the local write already succeeded;
    // the next successful write (or the next sync pass) naturally retries via upsert. Skipped
    // entirely (rather than awaiting the lock) while a syncWithRemote() is in flight, since that
    // sync's own push/pull already covers this row - the edit isn't lost, just picked up on the
    // next successful push or sync pass instead of duplicating work against a stale snapshot.
    private fun pushInBackground(entity: JournalEntryEntity) {
        val userId = authRepository.currentUserId() ?: return
        syncScope.launch {
            if (syncMutex.tryLock()) {
                try {
                    remoteDataSource.upsert(listOf(entity.toDomain().toRemoteDto(userId)))
                } finally {
                    syncMutex.unlock()
                }
            }
        }
    }

    // Same best-effort/skip-while-syncing shape as pushInBackground - only the id is needed here,
    // not a full entity, since deleting doesn't require the row's content.
    private fun pushDeleteInBackground(id: String) {
        authRepository.currentUserId() ?: return
        syncScope.launch {
            if (syncMutex.tryLock()) {
                try {
                    remoteDataSource.delete(id)
                } finally {
                    syncMutex.unlock()
                }
            }
        }
    }
}
