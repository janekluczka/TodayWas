package pl.luczka.todaywas.data.repository

interface RemoteJournalDataSource {

    suspend fun upsert(entries: List<JournalEntryRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<JournalEntryRemoteDto>>
}
