package pl.luczka.todaywas.data.remote.api

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto
import pl.luczka.todaywas.data.util.remoteCall
import javax.inject.Inject

private const val TABLE = "journal_entries"

class RemoteJournalDataSourceImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : RemoteJournalDataSource {

    // onConflict = "id" is required: journal_entries also has a UNIQUE(user_id, date) constraint
    // alongside its primary key, so Postgrest can't infer a single conflict target on its own -
    // without this, upserting an existing row falls back to a plain insert and 409s against that
    // second constraint instead of updating.
    override suspend fun upsert(entries: List<JournalEntryRemoteDto>): Result<Unit> = remoteCall {
        supabase.postgrest.from(TABLE).upsert(entries) { onConflict = "id" }
    }

    override suspend fun fetchAll(userId: String): Result<List<JournalEntryRemoteDto>> = remoteCall {
        supabase.postgrest
            .from(TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeList<JournalEntryRemoteDto>()
    }

    // lt("deleted_at", cutoff) alone already excludes active rows: Postgres evaluates
    // `NULL < cutoff` as NULL, which WHERE filters out - no separate "is not null" guard needed.
    override suspend fun purgeDeletedBefore(
        userId: String,
        cutoff: String,
    ): Result<Unit> = remoteCall {
        supabase.postgrest.from(TABLE).delete {
            filter {
                eq("user_id", userId)
                lt("deleted_at", cutoff)
            }
        }
    }
}
