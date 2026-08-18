package pl.luczka.todaywas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.entity.JournalEntryEntity

@Dao
interface JournalEntryDao {

    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL ORDER BY date DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL ORDER BY date DESC")
    suspend fun getAll(): List<JournalEntryEntity>

    // Sync-only: unlike getAll(), includes tombstoned rows so syncWithRemote() can compare them.
    @Query("SELECT * FROM journal_entries")
    suspend fun getAllIncludingDeleted(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): JournalEntryEntity?

    @Insert
    suspend fun insert(entity: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JournalEntryEntity)

    @Update
    suspend fun update(entity: JournalEntryEntity)

    @Query("UPDATE journal_entries SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        deletedAt: Long,
    )

    // GC only - hard-deletes tombstones older than the retention cutoff, both locally and (via the
    // repository's matching remote call) on the server.
    @Query("DELETE FROM journal_entries WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("DELETE FROM journal_entries")
    suspend fun clearAll()
}
