package pl.luczka.todaywas.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject

private const val TABLE = "habits"

class RemoteHabitDataSourceImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : RemoteHabitDataSource {

    override suspend fun upsert(habits: List<HabitRemoteDto>): Result<Unit> = remoteCall {
        supabase.postgrest.from(TABLE).upsert(habits)
    }

    override suspend fun fetchAll(userId: String): Result<List<HabitRemoteDto>> = remoteCall {
        supabase.postgrest
            .from(TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeList<HabitRemoteDto>()
    }
}
