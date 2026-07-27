package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.UserPreferencesDao
import pl.luczka.todaywas.data.local.UserPreferencesEntity
import pl.luczka.todaywas.domain.model.Focus

class OnboardingRepositoryImplTest {

    private class FakeDao(
        private val failuresBeforeSuccess: Int,
    ) : UserPreferencesDao {

        var upsertCallCount = 0
            private set

        override fun observe(): Flow<UserPreferencesEntity?> = flowOf(null)

        override suspend fun upsert(entity: UserPreferencesEntity) {
            upsertCallCount++
            if (upsertCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
        }
    }

    @Test
    fun `saveFocus retries once then succeeds if the retry works`() =
        runTest {
            val dao = FakeDao(failuresBeforeSuccess = 1)
            val repository = OnboardingRepositoryImpl(dao)

            val result = repository.saveFocus(Focus.JOURNAL)

            assertTrue(result.isSuccess)
            assertEquals(2, dao.upsertCallCount)
        }

    @Test
    fun `saveFocus returns failure after the retry also fails`() =
        runTest {
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = OnboardingRepositoryImpl(dao)

            val result = repository.saveFocus(Focus.JOURNAL)

            assertTrue(result.isFailure)
            assertEquals(2, dao.upsertCallCount)
        }
}
