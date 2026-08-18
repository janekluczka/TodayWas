package pl.luczka.todaywas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.entity.HabitEntity

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getAll(): List<HabitEntity>

    // Sync-only: unlike getAll(), includes tombstoned rows so syncWithRemote() can compare them.
    @Query("SELECT * FROM habits")
    suspend fun getAllIncludingDeleted(): List<HabitEntity>

    @Insert
    suspend fun insert(entity: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HabitEntity)

    @Query("UPDATE habits SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        deletedAt: Long,
    )

    // GC only - hard-deletes tombstones older than the retention cutoff, both locally and (via the
    // repository's matching remote call) on the server.
    @Query("DELETE FROM habits WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("DELETE FROM habits")
    suspend fun clearAll()
}
