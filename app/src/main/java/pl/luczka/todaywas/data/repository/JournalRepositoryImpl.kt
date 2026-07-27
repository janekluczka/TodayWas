package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.data.local.JournalEntryDao
import pl.luczka.todaywas.data.local.JournalEntryEntity
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val dao: JournalEntryDao,
) : JournalRepository {

    override fun observeEntries(): Flow<List<JournalEntry>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addEntry(
        date: LocalDate,
        text: String,
    ): Result<Unit> {
        val entity =
            JournalEntryEntity(
                date = date.toString(),
                text = text,
                createdAt = Instant.now().toEpochMilli(),
            )
        return try {
            dao.insert(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retry once before giving up, per the plan's write-resilience contract.
            try {
                dao.insert(entity)
                Result.success(Unit)
            } catch (retryException: CancellationException) {
                throw retryException
            } catch (retryException: Exception) {
                Result.failure(retryException)
            }
        }
    }
}
