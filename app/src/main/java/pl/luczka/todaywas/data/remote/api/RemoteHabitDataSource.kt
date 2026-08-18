package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.HabitRemoteDto

interface RemoteHabitDataSource {

    suspend fun upsert(habits: List<HabitRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<HabitRemoteDto>>

    // GC only - hard-deletes tombstones older than the cutoff (ISO-8601 instant). User-facing
    // delete is a normal upsert() of a soft-deleted row, not a call here.
    suspend fun purgeDeletedBefore(
        userId: String,
        cutoff: String,
    ): Result<Unit>
}
