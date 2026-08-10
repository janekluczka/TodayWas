package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
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
