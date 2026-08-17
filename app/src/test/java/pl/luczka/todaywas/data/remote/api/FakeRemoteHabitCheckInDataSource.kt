package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.HabitCheckInRemoteDto

class FakeRemoteHabitCheckInDataSource(
    private val checkIns: MutableMap<String, HabitCheckInRemoteDto> = mutableMapOf(),
    var shouldFail: Boolean = false,
) : RemoteHabitCheckInDataSource {

    var upsertCallCount = 0
        private set
    var deleteCallCount = 0
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

    override suspend fun delete(id: String): Result<Unit> {
        deleteCallCount++
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        checkIns.remove(id)
        return Result.success(Unit)
    }
}
