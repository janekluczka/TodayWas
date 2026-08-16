package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.HabitCheckInRemoteDto

interface RemoteHabitCheckInDataSource {

    suspend fun upsert(checkIns: List<HabitCheckInRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<HabitCheckInRemoteDto>>

    suspend fun delete(id: String): Result<Unit>
}
