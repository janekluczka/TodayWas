package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.UserPreferencesDao
import pl.luczka.todaywas.data.local.UserPreferencesEntity

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
    fun `should succeed after one retry when completeOnboarding's first write fails`() =
        runTest {
            // Arrange
            val dao = FakeDao(failuresBeforeSuccess = 1)
            val repository = OnboardingRepositoryImpl(dao)

            // Act
            val result = repository.completeOnboarding()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, dao.upsertCallCount)
        }

    @Test
    fun `should return failure when completeOnboarding's retry also fails`() =
        runTest {
            // Arrange
            val dao = FakeDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = OnboardingRepositoryImpl(dao)

            // Act
            val result = repository.completeOnboarding()

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, dao.upsertCallCount)
        }
}
