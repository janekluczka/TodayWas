package pl.luczka.todaywas.domain.entry.repository

interface Syncable {

    suspend fun syncWithRemote(): Result<Unit>

    suspend fun clearLocal(): Result<Unit>
}
