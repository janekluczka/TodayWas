package pl.luczka.todaywas.data.remote.api

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import pl.luczka.todaywas.data.remote.dto.HabitCheckInRemoteDto
import pl.luczka.todaywas.data.util.remoteCall
import javax.inject.Inject

private const val TABLE = "habit_check_ins"

class RemoteHabitCheckInDataSourceImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : RemoteHabitCheckInDataSource {

    override suspend fun upsert(checkIns: List<HabitCheckInRemoteDto>): Result<Unit> = remoteCall {
        supabase.postgrest.from(TABLE).upsert(checkIns)
    }

    override suspend fun fetchAll(userId: String): Result<List<HabitCheckInRemoteDto>> = remoteCall {
        supabase.postgrest
            .from(TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeList<HabitCheckInRemoteDto>()
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
