package pl.luczka.todaywas.data.remote.api

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import pl.luczka.todaywas.data.remote.dto.HabitRemoteDto
import pl.luczka.todaywas.data.repository.remoteCall
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
