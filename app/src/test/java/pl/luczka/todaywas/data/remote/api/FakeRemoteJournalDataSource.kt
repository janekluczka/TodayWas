package pl.luczka.todaywas.data.remote.api

import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto

class FakeRemoteJournalDataSource(
    private val entries: MutableMap<String, JournalEntryRemoteDto> = mutableMapOf(),
    var shouldFail: Boolean = false,
) : RemoteJournalDataSource {

    var upsertCallCount = 0
        private set
    var deleteCallCount = 0
        private set

    override suspend fun upsert(entries: List<JournalEntryRemoteDto>): Result<Unit> {
        upsertCallCount++
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        entries.forEach { this.entries[it.id] = it }
        return Result.success(Unit)
    }

    override suspend fun fetchAll(userId: String): Result<List<JournalEntryRemoteDto>> {
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        return Result.success(entries.values.filter { it.userId == userId })
    }

    override suspend fun delete(id: String): Result<Unit> {
        deleteCallCount++
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        entries.remove(id)
        return Result.success(Unit)
    }
}
