package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.HabitCheckInRemoteDto

class FakeRemoteHabitCheckInDataSource(
    private val checkIns: MutableMap<String, HabitCheckInRemoteDto> = mutableMapOf(),
    var shouldFail: Boolean = false,
    private val callOrderLog: MutableList<String>? = null,
) : RemoteHabitCheckInDataSource {

    var upsertCallCount = 0
        private set
    var purgeCallCount = 0
        private set

    override suspend fun upsert(checkIns: List<HabitCheckInRemoteDto>): Result<Unit> {
        upsertCallCount++
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        checkIns.forEach { this.checkIns[it.id] = it }
        return Result.success(Unit)
    }

    override suspend fun fetchAll(userId: String): Result<List<HabitCheckInRemoteDto>> {
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        return Result.success(checkIns.values.filter { it.userId == userId })
    }

    override suspend fun purgeDeletedBefore(
        userId: String,
        cutoff: String,
    ): Result<Unit> {
        purgeCallCount++
        callOrderLog?.add("check-in")
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        checkIns.values
            .filter { it.userId == userId && it.deletedAt != null && it.deletedAt < cutoff }
            .forEach { checkIns.remove(it.id) }
        return Result.success(Unit)
    }
}
