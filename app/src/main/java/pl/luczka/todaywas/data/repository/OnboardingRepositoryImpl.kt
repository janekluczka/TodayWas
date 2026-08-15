package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.local.UserPreferencesDao
import pl.luczka.todaywas.data.local.UserPreferencesEntity
import pl.luczka.todaywas.domain.model.OnboardingState
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    private val dao: UserPreferencesDao,
) : OnboardingRepository {

    override fun observeState(): Flow<OnboardingState> =
        dao.observe().map { entity ->
            OnboardingState(
                completed = entity?.onboardingCompleted ?: false,
                hasSyncedLocalData = entity?.hasSyncedLocalData ?: false,
            )
        }

    override suspend fun completeOnboarding(): Result<Unit> {
        val entity = dao.observe().first()?.copy(
            onboardingCompleted = true,
        ) ?: UserPreferencesEntity(
            onboardingCompleted = true,
        )
        return upsertWithRetry(entity)
    }

    override suspend fun markLocalDataSynced(): Result<Unit> {
        val entity = dao.observe().first()?.copy(hasSyncedLocalData = true) ?: return Result.success(Unit)
        return upsertWithRetry(entity)
    }

    override suspend fun resetSyncFlag(): Result<Unit> {
        val entity = dao.observe().first()?.copy(hasSyncedLocalData = false) ?: return Result.success(Unit)
        return upsertWithRetry(entity)
    }

    private suspend fun upsertWithRetry(entity: UserPreferencesEntity): Result<Unit> = try {
        dao.upsert(entity)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Retry once before giving up, per the plan's write-resilience contract.
        try {
            dao.upsert(entity)
            Result.success(Unit)
        } catch (retryException: CancellationException) {
            throw retryException
        } catch (retryException: Exception) {
            Result.failure(retryException)
        }
    }
}
