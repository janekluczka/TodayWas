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
import pl.luczka.todaywas.data.mapper.toSyncMeta
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSource
import pl.luczka.todaywas.data.util.TOMBSTONE_GC_WINDOW
import pl.luczka.todaywas.data.util.mergeForSync
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
        val entity = existing.copy(text = text, updatedAt = Instant.now().toEpochMilli())
        val result = safeDbCall { dao.update(entity) }
        if (result.isSuccess) pushInBackground(entity)
        return result
    }

    override suspend fun deleteEntry(id: String): Result<Unit> {
        val deletedAt = Instant.now().toEpochMilli()
        val existing = dao.getById(id)
        val result = safeDbCall { dao.softDeleteById(id, deletedAt) }
        // No dedicated remote delete call - the row's own deletedAt is the tombstone, carried to
        // remote by the same pushInBackground upsert path used for adds/edits.
        if (result.isSuccess) existing?.let { pushInBackground(it.copy(deletedAt = deletedAt)) }
        return result
    }

    override suspend fun syncWithRemote(): Result<Unit> {
        val userId = authRepository.currentUserId() ?: return Result.success(Unit)
        return syncMutex.withLock {
            remoteCall {
                val local = dao.getAllIncludingDeleted()
                val remote = remoteDataSource.fetchAll(userId).getOrThrow()
                val decision = mergeForSync(local.map { it.toSyncMeta() }, remote.map { it.toSyncMeta() })

                val toPush = local.filter { it.id in decision.pushIds }
                if (toPush.isNotEmpty()) {
                    remoteDataSource.upsert(toPush.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()
                }
                val toApply = remote.filter { it.id in decision.applyIds }
                toApply.forEach { dao.upsert(it.toEntity()) }

                val cutoff = Instant.now().minus(TOMBSTONE_GC_WINDOW)
                dao.purgeDeletedBefore(cutoff.toEpochMilli())
                remoteDataSource.purgeDeletedBefore(userId, cutoff.toString()).getOrThrow()
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
}
