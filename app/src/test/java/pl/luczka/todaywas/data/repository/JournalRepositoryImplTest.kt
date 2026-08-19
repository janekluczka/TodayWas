package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.api.LocalJournalDataSource
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.remote.api.FakeRemoteJournalDataSource
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSource
import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto
import pl.luczka.todaywas.data.util.TOMBSTONE_GC_WINDOW
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class JournalRepositoryImplTest {

    private fun repository(
        local: LocalJournalDataSource,
        scope: CoroutineScope,
        remote: RemoteJournalDataSource = FakeRemoteJournalDataSource(),
        auth: AuthRepository = FakeAuthRepository(),
    ) = JournalRepositoryImpl(local, remote, auth, scope)

    private class FakeLocalJournalDataSource(
        private val entities: MutableMap<String, JournalEntryEntity> = mutableMapOf(),
    ) : LocalJournalDataSource {

        var applyRemoteSnapshotCallCount = 0
            private set
        var lastApplied: List<JournalEntryEntity> = emptyList()
            private set
        var purgeDeletedBeforeCallCount = 0
            private set

        override fun observeEntries(): Flow<List<JournalEntryEntity>> = flowOf(entities.values.filter { it.deletedAt == null }.toList())

        override suspend fun getEntry(id: String): JournalEntryEntity? = entities[id]?.takeIf { it.deletedAt == null }

        override suspend fun insertEntry(entity: JournalEntryEntity): Result<Unit> {
            entities[entity.id] = entity
            return Result.success(Unit)
        }

        override suspend fun updateEntry(
            id: String,
            text: String,
            updatedAt: Long,
        ): Result<JournalEntryEntity> {
            val existing = entities[id] ?: return Result.failure(NoSuchElementException("Journal entry $id not found"))
            val updated = existing.copy(text = text, updatedAt = updatedAt)
            entities[id] = updated
            return Result.success(updated)
        }

        override suspend fun deleteEntry(
            id: String,
            deletedAt: Long,
        ): Result<JournalEntryEntity?> {
            val existing = entities[id]
            existing?.let { entities[id] = it.copy(deletedAt = deletedAt) }
            return Result.success(existing?.copy(deletedAt = deletedAt))
        }

        override suspend fun getAllIncludingDeleted(): List<JournalEntryEntity> = entities.values.toList()

        override suspend fun applyRemoteSnapshot(toApply: List<JournalEntryEntity>) {
            applyRemoteSnapshotCallCount++
            lastApplied = toApply
            toApply.forEach { entities[it.id] = it }
        }

        override suspend fun purgeDeletedBefore(cutoff: Long) {
            purgeDeletedBeforeCallCount++
            entities.values.filter { it.deletedAt != null && it.deletedAt < cutoff }.forEach { entities.remove(it.id) }
        }

        override suspend fun clearAll(): Result<Unit> {
            entities.clear()
            return Result.success(Unit)
        }
    }

    @Test
    fun `should return the mapped domain entry when getEntry finds it`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val local = FakeLocalJournalDataSource(entities = mutableMapOf("1" to entity))
            val repository = repository(local, backgroundScope)

            // Act
            val entry = repository.getEntry("1")

            // Assert
            assertEquals("1", entry?.id)
            assertEquals("Today was good.", entry?.text)
        }

    @Test
    fun `should return null when getEntry does not find it`() =
        runTest {
            // Arrange
            val local = FakeLocalJournalDataSource()
            val repository = repository(local, backgroundScope)

            // Act
            val entry = repository.getEntry("1")

            // Assert
            assertEquals(null, entry)
        }

    @Test
    fun `should return failure when updateEntry targets a missing id`() =
        runTest {
            // Arrange
            val local = FakeLocalJournalDataSource()
            val repository = repository(local, backgroundScope)

            // Act
            val result = repository.updateEntry("1", "Edited.")

            // Assert
            assertTrue(result.isFailure)
        }

    @Test
    fun `should push the tombstoned entry when signed in and deleteEntry succeeds`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val local = FakeLocalJournalDataSource(entities = mutableMapOf("1" to entity))
            val remote = FakeRemoteJournalDataSource()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local, backgroundScope, remote = remote, auth = auth)

            // Act
            repository.deleteEntry("1")
            runCurrent()

            // Assert
            assertEquals(1, remote.upsertCallCount)
        }

    @Test
    fun `should delete locally without any remote call when signed out`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val local = FakeLocalJournalDataSource(entities = mutableMapOf("1" to entity))
            val remote = FakeRemoteJournalDataSource()
            val auth = FakeAuthRepository(currentUserId = null)
            val repository = repository(local, backgroundScope, remote = remote, auth = auth)

            // Act
            val result = repository.deleteEntry("1")
            runCurrent()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(0, remote.upsertCallCount)
        }

    @Test
    fun `should still succeed when the background remote push fails`() =
        runTest {
            // Arrange
            val local = FakeLocalJournalDataSource()
            val remote = FakeRemoteJournalDataSource(shouldFail = true)
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local, backgroundScope, remote = remote, auth = auth)

            // Act
            val result = repository.addEntry(LocalDate.of(2026, 7, 27), "Today was good.")
            runCurrent()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, remote.upsertCallCount)
        }

    @Test
    fun `should not push when signed out`() =
        runTest {
            // Arrange
            val local = FakeLocalJournalDataSource()
            val remote = FakeRemoteJournalDataSource()
            val auth = FakeAuthRepository(currentUserId = null)
            val repository = repository(local, backgroundScope, remote = remote, auth = auth)

            // Act
            repository.addEntry(LocalDate.of(2026, 7, 27), "Today was good.")
            runCurrent()

            // Assert
            assertEquals(0, remote.upsertCallCount)
        }

    @Test
    fun `should push all local rows then apply the remote-only snapshot when syncWithRemote is called`() =
        runTest {
            // Arrange
            val localOnly =
                JournalEntryEntity(id = "local-1", date = "2026-07-27", text = "Local only.", createdAt = 1_000L, updatedAt = 1_000L)
            val local = FakeLocalJournalDataSource(entities = mutableMapOf("local-1" to localOnly))
            val remoteOnly = JournalEntryRemoteDto(
                id = "remote-1",
                userId = "user-1",
                date = "2026-07-20",
                text = "Remote only.",
                createdAt = "2026-07-20T00:00:00Z",
                updatedAt = "2026-07-20T00:00:00Z",
                deletedAt = null,
            )
            val remote = FakeRemoteJournalDataSource(entries = mutableMapOf("remote-1" to remoteOnly))
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local, backgroundScope, remote = remote, auth = auth)

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, remote.upsertCallCount)
            assertEquals(1, local.applyRemoteSnapshotCallCount)
            assertTrue(local.lastApplied.any { it.id == "remote-1" })
        }

    @Test
    fun `should not resurrect a remotely-deleted row during syncWithRemote`() =
        runTest {
            // Arrange - local has a stale, unsynced, non-deleted edit with a *newer* updatedAt than
            // the remote tombstone, exercising tombstone supremacy over plain timestamp comparison.
            val staleEdit =
                JournalEntryEntity(id = "1", date = "2026-07-27", text = "Stale edit.", createdAt = 1_000L, updatedAt = 9_999_999L)
            val local = FakeLocalJournalDataSource(entities = mutableMapOf("1" to staleEdit))
            val remoteTombstone = JournalEntryRemoteDto(
                id = "1",
                userId = "user-1",
                date = "2026-07-27",
                text = "Deleted elsewhere.",
                createdAt = "2026-07-01T00:00:00Z",
                updatedAt = "2026-07-02T00:00:00Z",
                deletedAt = "2026-07-02T00:00:00Z",
            )
            val remote = FakeRemoteJournalDataSource(entries = mutableMapOf("1" to remoteTombstone))
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local, backgroundScope, remote = remote, auth = auth)

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(null, local.getEntry("1"))
            assertEquals(0, remote.upsertCallCount)
        }

    @Test
    fun `should purge tombstones older than the GC window during syncWithRemote`() =
        runTest {
            // Arrange
            val agedOut = Instant
                .now()
                .minus(TOMBSTONE_GC_WINDOW)
                .minusSeconds(3_600)
                .toEpochMilli()
            val tombstoned = JournalEntryEntity(
                id = "1",
                date = "2026-01-01",
                text = "Long gone.",
                createdAt = 1_000L,
                updatedAt = agedOut,
                deletedAt = agedOut,
            )
            val local = FakeLocalJournalDataSource(entities = mutableMapOf("1" to tombstoned))
            val remote = FakeRemoteJournalDataSource()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local, backgroundScope, remote = remote, auth = auth)

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, local.purgeDeletedBeforeCallCount)
            assertTrue(local.getAllIncludingDeleted().none { it.id == "1" })
            assertEquals(1, remote.purgeCallCount)
        }

    @Test
    fun `should delegate to the local data source when clearLocal is called`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val local = FakeLocalJournalDataSource(entities = mutableMapOf("1" to entity))
            val repository = repository(local, backgroundScope)

            // Act
            val result = repository.clearLocal()

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(local.getAllIncludingDeleted().isEmpty())
        }
}
