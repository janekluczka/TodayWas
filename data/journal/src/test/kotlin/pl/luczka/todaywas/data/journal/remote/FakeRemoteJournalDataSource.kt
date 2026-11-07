package pl.luczka.todaywas.data.journal.remote

import pl.luczka.todaywas.data.journal.remote.JournalEntryRemoteDto
import pl.luczka.todaywas.data.journal.remote.RemoteJournalDataSource

class FakeRemoteJournalDataSource(
    private val entries: MutableMap<String, JournalEntryRemoteDto> = mutableMapOf(),
    var shouldFail: Boolean = false,
) : RemoteJournalDataSource {

    var upsertCallCount = 0
        private set
    var purgeCallCount = 0
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

    override suspend fun purgeDeletedBefore(
        userId: String,
        cutoff: String,
    ): Result<Unit> {
        purgeCallCount++
        if (shouldFail) return Result.failure(RuntimeException("simulated remote failure"))
        entries.values
            .filter { it.userId == userId && it.deletedAt?.let { deletedAt -> deletedAt < cutoff } == true }
            .forEach { entries.remove(it.id) }
        return Result.success(Unit)
    }
}
