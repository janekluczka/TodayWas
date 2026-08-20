package pl.luczka.todaywas.data.local.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.dao.JournalEntryDao
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity

@OptIn(ExperimentalCoroutinesApi::class)
class LocalJournalDataSourceImplTest {

    private fun dataSource(dao: JournalEntryDao) = LocalJournalDataSourceImpl(dao)

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
        var upsertAllCallCount = 0
            private set

        override fun observeAll(): Flow<List<JournalEntryEntity>> =
            flowOf(entities.values.filter { it.deletedAt == null }.toList())

        override suspend fun getAll(): List<JournalEntryEntity> = entities.values
            .filter {
                it.deletedAt ==
                    null
            }.toList()

        override suspend fun getAllIncludingDeleted(): List<JournalEntryEntity> = entities.values
            .toList()

        override suspend fun getById(id: String): JournalEntryEntity? = entities[id]?.takeIf {
            it.deletedAt ==
                null
        }

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

        override suspend fun upsertAll(entities: List<JournalEntryEntity>) {
            upsertAllCallCount++
            entities.forEach { this.entities[it.id] = it }
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
            entities.values
                .filter {
                    it.deletedAt != null && it.deletedAt < cutoff
                }.forEach { entities.remove(it.id) }
        }

        override suspend fun clearAll() {
            entities.clear()
        }
    }

    @Test
    fun `should succeed after one retry when insertEntry's first write fails`() =
        runTest {
            // Arrange
            val dao = FakeDao(failuresBeforeSuccess = 1)
            val dataSource = dataSource(dao)
            val entity =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )

            // Act
            val result = dataSource.insertEntry(entity)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, dao.insertCallCount)
        }

    @Test
    fun `should return failure when insertEntry's retry also fails`() =
        runTest {
            // Arrange
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val dataSource = dataSource(dao)
            val entity =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )

            // Act
            val result = dataSource.insertEntry(entity)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, dao.insertCallCount)
        }

    @Test
    fun `should succeed after one retry when updateEntry's first write fails`() =
        runTest {
            // Arrange
            val entity =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-07-27",
                    text = "Original.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )
            val dao = FakeDao(failuresBeforeSuccess = 1, entities = mutableMapOf("1" to entity))
            val dataSource = dataSource(dao)

            // Act
            val result = dataSource.updateEntry(id = "1", text = "Edited.", updatedAt = 2_000L)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("Edited.", result.getOrNull()?.text)
            assertEquals(2, dao.updateCallCount)
        }

    @Test
    fun `should return failure when updateEntry's retry also fails`() =
        runTest {
            // Arrange
            val entity =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-07-27",
                    text = "Original.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )
            val dao = FakeDao(
                failuresBeforeSuccess = Int.MAX_VALUE,
                entities = mutableMapOf(
                    "1" to entity,
                ),
            )
            val dataSource = dataSource(dao)

            // Act
            val result = dataSource.updateEntry(id = "1", text = "Edited.", updatedAt = 2_000L)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, dao.updateCallCount)
        }

    @Test
    fun `should return failure when updateEntry targets a missing id`() =
        runTest {
            // Arrange
            val dao = FakeDao()
            val dataSource = dataSource(dao)

            // Act
            val result = dataSource.updateEntry(id = "1", text = "Edited.", updatedAt = 2_000L)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, dao.updateCallCount)
        }

    @Test
    fun `should soft-delete and return the entry when deleteEntry finds it`() =
        runTest {
            // Arrange
            val entity =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )
            val dao = FakeDao(entities = mutableMapOf("1" to entity))
            val dataSource = dataSource(dao)

            // Act
            val result = dataSource.deleteEntry(id = "1", deletedAt = 2_000L)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("1", result.getOrNull()?.id)
            assertEquals(null, dao.getById("1"))
        }

    @Test
    fun `should succeed with a null entry when deleteEntry targets a missing id`() =
        runTest {
            // Arrange
            val dao = FakeDao()
            val dataSource = dataSource(dao)

            // Act
            val result = dataSource.deleteEntry(id = "1", deletedAt = 2_000L)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrNull())
        }

    @Test
    fun `should upsert all entries when applyRemoteSnapshot is called`() =
        runTest {
            // Arrange
            val dao = FakeDao()
            val dataSource = dataSource(dao)
            val entity =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )

            // Act
            dataSource.applyRemoteSnapshot(listOf(entity))

            // Assert
            assertEquals(1, dao.upsertAllCallCount)
            assertTrue(dao.getAll().any { it.id == "1" })
        }

    @Test
    fun `should remove tombstoned rows older than cutoff when purgeDeletedBefore is called`() =
        runTest {
            // Arrange
            val agedOut =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-01-01",
                    text = "Long gone.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    deletedAt = 500L,
                )
            val dao = FakeDao(entities = mutableMapOf("1" to agedOut))
            val dataSource = dataSource(dao)

            // Act
            dataSource.purgeDeletedBefore(cutoff = 1_000L)

            // Assert
            assertTrue(dao.getAllIncludingDeleted().isEmpty())
        }

    @Test
    fun `should empty the DAO when clearAll is called`() =
        runTest {
            // Arrange
            val entity =
                JournalEntryEntity(
                    id = "1",
                    date = "2026-07-27",
                    text = "Today was good.",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )
            val dao = FakeDao(entities = mutableMapOf("1" to entity))
            val dataSource = dataSource(dao)

            // Act
            val result = dataSource.clearAll()

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(dao.getAll().isEmpty())
        }
}
