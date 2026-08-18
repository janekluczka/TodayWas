package pl.luczka.todaywas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity

@Dao
interface HabitCheckInDao {

    @Query("SELECT * FROM habit_check_ins WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<HabitCheckInEntity>>

    @Query("SELECT * FROM habit_check_ins WHERE deletedAt IS NULL")
    suspend fun getAll(): List<HabitCheckInEntity>

    // Sync-only: unlike getAll(), includes tombstoned rows so syncWithRemote() can compare them.
    @Query("SELECT * FROM habit_check_ins")
    suspend fun getAllIncludingDeleted(): List<HabitCheckInEntity>

    @Query("SELECT * FROM habit_check_ins WHERE habitId = :habitId AND deletedAt IS NULL")
    suspend fun getByHabitId(habitId: String): List<HabitCheckInEntity>

    @Query("SELECT * FROM habit_check_ins WHERE habitId = :habitId AND date = :date AND deletedAt IS NULL")
    suspend fun getByHabitAndDate(
        habitId: String,
        date: String,
    ): HabitCheckInEntity?

    @Insert
    suspend fun insertOne(entity: HabitCheckInEntity)

    @Transaction
    suspend fun insertAll(entities: List<HabitCheckInEntity>) {
        entities.forEach { insertOne(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(entity: HabitCheckInEntity)

    @Transaction
    suspend fun upsertAll(entities: List<HabitCheckInEntity>) {
        entities.forEach { upsertOne(it) }
    }

    @Update
    suspend fun update(entity: HabitCheckInEntity)

    @Query("UPDATE habit_check_ins SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        deletedAt: Long,
    )

    @Query("UPDATE habit_check_ins SET deletedAt = :deletedAt WHERE habitId = :habitId AND deletedAt IS NULL")
    suspend fun softDeleteByHabitId(
        habitId: String,
        deletedAt: Long,
    )

    // GC only - hard-deletes tombstones older than the retention cutoff, both locally and (via the
    // repository's matching remote call) on the server.
    @Query("DELETE FROM habit_check_ins WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("DELETE FROM habit_check_ins")
    suspend fun clearAll()
}
