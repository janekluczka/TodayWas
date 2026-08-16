package pl.luczka.todaywas.data.remote.api

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto
import pl.luczka.todaywas.data.repository.remoteCall
import javax.inject.Inject

private const val TABLE = "journal_entries"

class RemoteJournalDataSourceImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : RemoteJournalDataSource {

    override suspend fun upsert(entries: List<JournalEntryRemoteDto>): Result<Unit> = remoteCall {
        supabase.postgrest.from(TABLE).upsert(entries)
    }

    override suspend fun fetchAll(userId: String): Result<List<JournalEntryRemoteDto>> = remoteCall {
        supabase.postgrest
            .from(TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeList<JournalEntryRemoteDto>()
    }
}
