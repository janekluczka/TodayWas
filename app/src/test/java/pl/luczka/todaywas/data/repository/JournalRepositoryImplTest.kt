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
        private val failuresBeforeSuccess: Int,
    ) : JournalEntryDao {

        var insertCallCount = 0
            private set

        override fun observeAll(): Flow<List<JournalEntryEntity>> = flowOf(emptyList())

        override suspend fun insert(entity: JournalEntryEntity) {
            insertCallCount++
            if (insertCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
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
}
