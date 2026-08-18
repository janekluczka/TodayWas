package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.HabitCheckInRemoteDto

interface RemoteHabitCheckInDataSource {

    suspend fun upsert(checkIns: List<HabitCheckInRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<HabitCheckInRemoteDto>>

    // GC only - hard-deletes tombstones older than the cutoff (ISO-8601 instant). User-facing
    // delete is a normal upsert() of a soft-deleted row, not a call here.
    suspend fun purgeDeletedBefore(
        userId: String,
        cutoff: String,
    ): Result<Unit>
}
