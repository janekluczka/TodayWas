package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.JournalEntryDao
import pl.luczka.todaywas.data.local.JournalEntryEntity
import java.time.LocalDate

class JournalRepositoryImplTest {

    private class FakeDao(
        private val failuresBeforeSuccess: Int = 0,
        private val entities: MutableMap<Long, JournalEntryEntity> = mutableMapOf(),
    ) : JournalEntryDao {

        var insertCallCount = 0
            private set
        var updateCallCount = 0
            private set

        override fun observeAll(): Flow<List<JournalEntryEntity>> = flowOf(entities.values.toList())

        override suspend fun getById(id: Long): JournalEntryEntity? = entities[id]

        override suspend fun insert(entity: JournalEntryEntity) {
            insertCallCount++
            if (insertCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
        }

        override suspend fun update(entity: JournalEntryEntity) {
            updateCallCount++
            if (updateCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.id] = entity
        }
    }

    @Test
    fun `should succeed after one retry when addEntry's first write fails`() =
        runTest {
            // Arrange
            val dao = FakeDao(failuresBeforeSuccess = 1)
            val repository = JournalRepositoryImpl(dao)

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
            val repository = JournalRepositoryImpl(dao)

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
            val entity = JournalEntryEntity(id = 1L, date = "2026-07-27", text = "Today was good.", createdAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf(1L to entity))
            val repository = JournalRepositoryImpl(dao)

            // Act
            val entry = repository.getEntry(1L)

            // Assert
            assertEquals(1L, entry?.id)
            assertEquals("Today was good.", entry?.text)
        }

    @Test
    fun `should return null when getEntry does not find it`() =
        runTest {
            // Arrange
            val dao = FakeDao()
            val repository = JournalRepositoryImpl(dao)

            // Act
            val entry = repository.getEntry(1L)

            // Assert
            assertEquals(null, entry)
        }

    @Test
    fun `should succeed after one retry when updateEntry's first write fails`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = 1L, date = "2026-07-27", text = "Original.", createdAt = 1_000L)
            val dao = FakeDao(failuresBeforeSuccess = 1, entities = mutableMapOf(1L to entity))
            val repository = JournalRepositoryImpl(dao)

            // Act
            val result = repository.updateEntry(1L, "Edited.")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, dao.updateCallCount)
            assertEquals("Edited.", dao.getById(1L)?.text)
        }

    @Test
    fun `should return failure when updateEntry's retry also fails`() =
        runTest {
            // Arrange
            val entity = JournalEntryEntity(id = 1L, date = "2026-07-27", text = "Original.", createdAt = 1_000L)
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE, entities = mutableMapOf(1L to entity))
            val repository = JournalRepositoryImpl(dao)

            // Act
            val result = repository.updateEntry(1L, "Edited.")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, dao.updateCallCount)
        }

    @Test
    fun `should return failure when updateEntry targets a missing id`() =
        runTest {
            // Arrange
            val dao = FakeDao()
            val repository = JournalRepositoryImpl(dao)

            // Act
            val result = repository.updateEntry(1L, "Edited.")

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, dao.updateCallCount)
        }
}
