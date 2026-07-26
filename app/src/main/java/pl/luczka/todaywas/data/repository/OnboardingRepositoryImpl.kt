package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.local.UserPreferencesDao
import pl.luczka.todaywas.data.local.UserPreferencesEntity
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.OnboardingState
import javax.inject.Inject

class OnboardingRepositoryImpl
    @Inject
    constructor(
        private val dao: UserPreferencesDao,
    ) : OnboardingRepository {
        override fun observeState(): Flow<OnboardingState> =
            dao.observe().map { entity ->
                OnboardingState(
                    completed = entity?.onboardingCompleted ?: false,
                    focus = entity?.focus?.let { Focus.valueOf(it) },
                )
            }

        override suspend fun saveFocus(focus: Focus): Result<Unit> {
            val entity = UserPreferencesEntity(focus = focus.name, onboardingCompleted = true)
            return try {
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
    }
