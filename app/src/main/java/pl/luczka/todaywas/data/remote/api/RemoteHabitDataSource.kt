package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.HabitRemoteDto

interface RemoteHabitDataSource {

    suspend fun upsert(habits: List<HabitRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<HabitRemoteDto>>
}
