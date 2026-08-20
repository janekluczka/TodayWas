package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.luczka.todaywas.data.local.api.LocalJournalDataSource
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.mapper.toDomain
import pl.luczka.todaywas.data.mapper.toEntity
import pl.luczka.todaywas.data.mapper.toRemoteDto
import pl.luczka.todaywas.data.mapper.toSyncMeta
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSource
import pl.luczka.todaywas.data.util.SyncScheduler
import pl.luczka.todaywas.data.util.TOMBSTONE_GC_WINDOW
import pl.luczka.todaywas.data.util.mergeForSync
import pl.luczka.todaywas.data.util.remoteCall
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.JournalRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val local: LocalJournalDataSource,
    private val remoteDataSource: RemoteJournalDataSource,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
) : JournalRepository {

    // Guards against two concurrent syncWithRemote() calls racing each other - writes no longer
    // push individually in the background, so there's nothing else left for this to race against.
    private val syncMutex = Mutex()

    override fun observeEntries(): Flow<List<JournalEntry>> = local.observeEntries().toDomain()

    override suspend fun getEntry(id: String): JournalEntry? = local.getEntry(id)?.toDomain()

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
        val result = local.insertEntry(entity)
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result
    }

    override suspend fun updateEntry(
        id: String,
        text: String,
    ): Result<Unit> {
        val result = local.updateEntry(id, text, Instant.now().toEpochMilli())
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result.map { }
    }

    override suspend fun deleteEntry(id: String): Result<Unit> {
        val result = local.deleteEntry(id, Instant.now().toEpochMilli())
        if (result.isSuccess) scheduleSyncIfSignedIn()
        return result.map { }
    }

    override suspend fun syncWithRemote(): Result<Unit> {
        val userId = authRepository.currentUserId() ?: return Result.success(Unit)
        return syncMutex.withLock {
            remoteCall {
                val localEntries = local.getAllIncludingDeleted()
                val remote = remoteDataSource.fetchAll(userId).getOrThrow()
                val decision = mergeForSync(localEntries.toSyncMeta(), remote.toSyncMeta())

                val toPush = localEntries.filter { it.id in decision.pushIds }
                if (toPush.isNotEmpty()) {
                    remoteDataSource.upsert(toPush.toRemoteDto(userId)).getOrThrow()
                }
                val toApply = remote.filter { it.id in decision.applyIds }
                local.applyRemoteSnapshot(toApply.toEntity())

                val cutoff = Instant.now().minus(TOMBSTONE_GC_WINDOW)
                local.purgeDeletedBefore(cutoff.toEpochMilli())
                remoteDataSource.purgeDeletedBefore(userId, cutoff.toString()).getOrThrow()
            }
        }
    }

    override suspend fun clearLocal(): Result<Unit> = local.clearAll()

    private fun scheduleSyncIfSignedIn() {
        if (authRepository.currentUserId() != null) syncScheduler.scheduleSync()
    }
}
