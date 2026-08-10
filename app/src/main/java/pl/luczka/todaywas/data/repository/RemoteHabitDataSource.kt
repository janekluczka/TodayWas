package pl.luczka.todaywas.data.repository

interface RemoteHabitDataSource {

    suspend fun upsert(habits: List<HabitRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<HabitRemoteDto>>
}
