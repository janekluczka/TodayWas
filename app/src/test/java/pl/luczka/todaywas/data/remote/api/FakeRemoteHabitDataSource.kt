package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.HabitRemoteDto

class FakeRemoteHabitDataSource(
    private val habits: MutableMap<String, HabitRemoteDto> = mutableMapOf(),
    var shouldFail: Boolean = false,
) : RemoteHabitDataSource {

    var upsertCallCount = 0
        private set
    var purgeCallCount = 0
        private set

    override suspend fun upsert(habits: List<HabitRemoteDto>): Result<Unit> {
        upsertCallCount++
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        habits.forEach { this.habits[it.id] = it }
        return Result.success(Unit)
    }

    override suspend fun fetchAll(userId: String): Result<List<HabitRemoteDto>> {
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        return Result.success(habits.values.filter { it.userId == userId })
    }

    override suspend fun purgeDeletedBefore(
        userId: String,
        cutoff: String,
    ): Result<Unit> {
        purgeCallCount++
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        habits.values
            .filter { it.userId == userId && it.deletedAt != null && it.deletedAt < cutoff }
            .forEach { habits.remove(it.id) }
        return Result.success(Unit)
    }
}
