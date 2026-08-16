package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto

interface RemoteJournalDataSource {

    suspend fun upsert(entries: List<JournalEntryRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<JournalEntryRemoteDto>>

    suspend fun delete(id: String): Result<Unit>
}
