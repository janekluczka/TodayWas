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
import pl.luczka.todaywas.data.local.dao.JournalEntryDao
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.remote.api.FakeRemoteJournalDataSource
import pl.luczka.todaywas.data.remote.api.RemoteJournalDataSource
import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class JournalRepositoryImplTest {

    private fun repository(
        dao: JournalEntryDao,
        scope: CoroutineScope,
        remote: RemoteJournalDataSource = FakeRemoteJournalDataSource(),
        auth: AuthRepository = FakeAuthRepository(),
    ) = JournalRepositoryImpl(dao, remote, auth, scope)

    private class FakeDao(
        private val failuresBeforeSuccess: Int = 0,
        private val entities: MutableMap<String, JournalEntryEntity> = mutableMapOf(),
    ) : JournalEntryDao {

        var insertCallCount = 0
            private set
        var updateCallCount = 0
            private set
        var deleteCallCount = 0
            private set

        override fun observeAll(): Flow<List<JournalEntryEntity>> =
            flowOf(entities.values.filter { it.deletedAt == null }.toList())

        override suspend fun getAll(): List<JournalEntryEntity> = entities.values.filter { it.deletedAt == null }.toList()

        override suspend fun getAllIncludingDeleted(): List<JournalEntryEntity> = entities.values.toList()

        override suspend fun getById(id: String): JournalEntryEntity? = entities[id]?.takeIf { it.deletedAt == null }

        override suspend fun insert(entity: JournalEntryEntity) {
            insertCallCount++
            if (insertCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.id] = entity
        }

        override suspend fun upsert(entity: JournalEntryEntity) {
            entities[entity.id] = entity
        }

        override suspend fun update(entity: JournalEntryEntity) {
            updateCallCount++
            if (updateCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.id] = entity
        }

        override suspend fun softDeleteById(
            id: String,
            deletedAt: Long,
        ) {
            deleteCallCount++
            if (deleteCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[id]?.let { entities[id] = it.copy(deletedAt = deletedAt) }
        }

        override suspend fun purgeDeletedBefore(cutoff: Long) {
            entities.values.filter { it.deletedAt != null && it.deletedAt < cutoff }.forEach { entities.remove(it.id) }
        }

        override suspend fun clearAll() {
            entities.clear()
        }
    }

    @Test
    fun `should succeed after one retry when addEntry's first write fails`() =
        runTest {
            // Arrange
            val dao = FakeDao(failuresBeforeSuccess = 1)
            val repository = repository(dao, backgroundScope)

            // Act
            val result = repository.addEntry(LocalDate.of(2026, 7, 27), "Today was good.")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, dao.insertCallCount)
        }

    @Test
    fun `should return failure when addEntry's retry also fails`() =
        runTest {
            // Arrange
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = repository(dao, backgroundScope)

            // Act
            val result = repository.addEntry(LocalDate.of(2026, 7, 27), "Today was good.")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, dao.insertCallCount)
        }

    @Test
    fun `should return the mapped domain entry when getEntry finds it`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf("1" to entity))
            val repository = repository(dao, backgroundScope)

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
            val dao = FakeDao()
            val repository = repository(dao, backgroundScope)

            // Act
            val entry = repository.getEntry("1")

            // Assert
            assertEquals(null, entry)
        }

    @Test
    fun `should succeed after one retry when updateEntry's first write fails`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Original.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(failuresBeforeSuccess = 1, entities = mutableMapOf("1" to entity))
            val repository = repository(dao, backgroundScope)

            // Act
            val result = repository.updateEntry("1", "Edited.")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, dao.updateCallCount)
            assertEquals("Edited.", dao.getById("1")?.text)
        }

    @Test
    fun `should return failure when updateEntry's retry also fails`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Original.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE, entities = mutableMapOf("1" to entity))
            val repository = repository(dao, backgroundScope)

            // Act
            val result = repository.updateEntry("1", "Edited.")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, dao.updateCallCount)
        }

    @Test
    fun `should return failure when updateEntry targets a missing id`() =
        runTest {
            // Arrange
            val dao = FakeDao()
            val repository = repository(dao, backgroundScope)

            // Act
            val result = repository.updateEntry("1", "Edited.")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, dao.updateCallCount)
        }

    @Test
    fun `should remove the entry from the DAO when deleteEntry is called`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf("1" to entity))
            val repository = repository(dao, backgroundScope)

            // Act
            val result = repository.deleteEntry("1")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(null, dao.getById("1"))
        }

    @Test
    fun `should push the remote delete when signed in and deleteEntry succeeds`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf("1" to entity))
            val remote = FakeRemoteJournalDataSource()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(dao, backgroundScope, remote = remote, auth = auth)

            // Act
            repository.deleteEntry("1")
            runCurrent()

            // Assert
            assertEquals(1, remote.deleteCallCount)
        }

    @Test
    fun `should delete locally without any remote call when signed out`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf("1" to entity))
            val remote = FakeRemoteJournalDataSource()
            val auth = FakeAuthRepository(currentUserId = null)
            val repository = repository(dao, backgroundScope, remote = remote, auth = auth)

            // Act
            val result = repository.deleteEntry("1")
            runCurrent()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(null, dao.getById("1"))
            assertEquals(0, remote.deleteCallCount)
        }

    @Test
    fun `should still succeed when the background remote push fails`() =
        runTest {
            // Arrange
            val dao = FakeDao()
            val remote = FakeRemoteJournalDataSource(shouldFail = true)
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(dao, backgroundScope, remote = remote, auth = auth)

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
            val dao = FakeDao()
            val remote = FakeRemoteJournalDataSource()
            val auth = FakeAuthRepository(currentUserId = null)
            val repository = repository(dao, backgroundScope, remote = remote, auth = auth)

            // Act
            repository.addEntry(LocalDate.of(2026, 7, 27), "Today was good.")
            runCurrent()

            // Assert
            assertEquals(0, remote.upsertCallCount)
        }

    @Test
    fun `should push all local rows then pull remote-only rows when syncWithRemote is called`() =
        runTest {
            // Arrange
            val localOnly =
                JournalEntryEntity(id = "local-1", date = "2026-07-27", text = "Local only.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf("local-1" to localOnly))
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
            val repository = repository(dao, backgroundScope, remote = remote, auth = auth)

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, remote.upsertCallCount)
            assertTrue(dao.getAll().any { it.id == "remote-1" })
        }

    @Test
    fun `should empty the DAO when clearLocal is called`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = "1", date = "2026-07-27", text = "Today was good.", createdAt = 1_000L, updatedAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf("1" to entity))
            val repository = repository(dao, backgroundScope)

            // Act
            val result = repository.clearLocal()

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(dao.getAll().isEmpty())
        }
}
