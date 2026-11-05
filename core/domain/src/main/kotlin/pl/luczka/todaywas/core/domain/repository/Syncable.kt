package pl.luczka.todaywas.core.domain.repository

interface Syncable {

    suspend fun syncWithRemote(): Result<Unit>

    suspend fun clearLocal(): Result<Unit>
}
