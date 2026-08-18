package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto

interface RemoteJournalDataSource {

    suspend fun upsert(entries: List<JournalEntryRemoteDto>): Result<Unit>

    suspend fun fetchAll(userId: String): Result<List<JournalEntryRemoteDto>>

    // GC only - hard-deletes tombstones older than the cutoff (ISO-8601 instant). User-facing
    // delete is a normal upsert() of a soft-deleted row, not a call here.
    suspend fun purgeDeletedBefore(
        userId: String,
        cutoff: String,
    ): Result<Unit>
}
