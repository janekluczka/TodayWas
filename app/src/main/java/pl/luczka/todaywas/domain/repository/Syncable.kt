package pl.luczka.todaywas.domain.repository

interface Syncable {

    suspend fun syncWithRemote(): Result<Unit>

    suspend fun clearLocal(): Result<Unit>
}
