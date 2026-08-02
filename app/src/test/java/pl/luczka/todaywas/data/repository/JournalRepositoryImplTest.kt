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
    fun `addEntry retries once then succeeds if the retry works`() =
        runTest {
            val dao = FakeDao(failuresBeforeSuccess = 1)
            val repository = JournalRepositoryImpl(dao)

            val result = repository.addEntry(LocalDate.of(2026, 7, 27), "Today was good.")

            assertTrue(result.isSuccess)
            assertEquals(2, dao.insertCallCount)
        }

    @Test
    fun `addEntry returns failure after the retry also fails`() =
        runTest {
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = JournalRepositoryImpl(dao)

            val result = repository.addEntry(LocalDate.of(2026, 7, 27), "Today was good.")

            assertTrue(result.isFailure)
            assertEquals(2, dao.insertCallCount)
        }

    @Test
    fun `getEntry returns the mapped domain entry when found`() =
        runTest {
            val entity = JournalEntryEntity(id = 1L, date = "2026-07-27", text = "Today was good.", createdAt = 1_000L)
            val dao = FakeDao(entities = mutableMapOf(1L to entity))
            val repository = JournalRepositoryImpl(dao)

            val entry = repository.getEntry(1L)

            assertEquals(1L, entry?.id)
            assertEquals("Today was good.", entry?.text)
        }

    @Test
    fun `getEntry returns null when not found`() =
        runTest {
            val dao = FakeDao()
            val repository = JournalRepositoryImpl(dao)

            val entry = repository.getEntry(1L)

            assertEquals(null, entry)
        }

    @Test
    fun `updateEntry retries once then succeeds if the retry works`() =
        runTest {
            val entity = JournalEntryEntity(id = 1L, date = "2026-07-27", text = "Original.", createdAt = 1_000L)
            val dao = FakeDao(failuresBeforeSuccess = 1, entities = mutableMapOf(1L to entity))
            val repository = JournalRepositoryImpl(dao)

            val result = repository.updateEntry(1L, "Edited.")

            assertTrue(result.isSuccess)
            assertEquals(2, dao.updateCallCount)
            assertEquals("Edited.", dao.getById(1L)?.text)
        }

    @Test
    fun `updateEntry returns failure after the retry also fails`() =
        runTest {
            val entity = JournalEntryEntity(id = 1L, date = "2026-07-27", text = "Original.", createdAt = 1_000L)
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE, entities = mutableMapOf(1L to entity))
            val repository = JournalRepositoryImpl(dao)

            val result = repository.updateEntry(1L, "Edited.")

            assertTrue(result.isFailure)
            assertEquals(2, dao.updateCallCount)
        }

    @Test
    fun `updateEntry returns failure for a missing id`() =
        runTest {
            val dao = FakeDao()
            val repository = JournalRepositoryImpl(dao)

            val result = repository.updateEntry(1L, "Edited.")

            assertTrue(result.isFailure)
            assertEquals(0, dao.updateCallCount)
        }
}
