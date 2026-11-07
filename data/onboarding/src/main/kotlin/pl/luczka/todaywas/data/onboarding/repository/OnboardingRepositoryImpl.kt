package pl.luczka.todaywas.data.onboarding.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.core.database.dao.UserPreferencesDao
import pl.luczka.todaywas.core.database.entity.UserPreferencesEntity
import pl.luczka.todaywas.core.database.util.safeDbCall
import pl.luczka.todaywas.domain.onboarding.model.OnboardingState
import pl.luczka.todaywas.domain.onboarding.repository.OnboardingRepository

class OnboardingRepositoryImpl @Inject constructor(
    private val dao: UserPreferencesDao,
) : OnboardingRepository {

    override fun observeState(): Flow<OnboardingState> = dao.observe().map { entity ->
        OnboardingState(
            completed = entity?.onboardingCompleted ?: false,
            hasSyncedLocalData = entity?.hasSyncedLocalData ?: false,
        )
    }

    override suspend fun completeOnboarding(): Result<Unit> {
        val entity = dao.observe().first()?.copy(onboardingCompleted = true) ?: UserPreferencesEntity(
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

    private suspend fun upsertWithRetry(entity: UserPreferencesEntity): Result<Unit> = safeDbCall { dao.upsert(entity) }
}
