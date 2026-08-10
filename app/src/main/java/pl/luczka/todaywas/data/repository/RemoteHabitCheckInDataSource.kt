package pl.luczka.todaywas.data.repository

interface RemoteHabitCheckInDataSource {

    suspend fun upsert(checkIns: List<HabitCheckInRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<HabitCheckInRemoteDto>>
}
