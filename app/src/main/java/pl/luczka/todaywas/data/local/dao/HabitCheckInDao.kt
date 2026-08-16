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

    @Query("SELECT * FROM habit_check_ins")
    fun observeAll(): Flow<List<HabitCheckInEntity>>

    @Query("SELECT * FROM habit_check_ins")
    suspend fun getAll(): List<HabitCheckInEntity>

    @Query("SELECT * FROM habit_check_ins WHERE habitId = :habitId AND date = :date")
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

    @Query("DELETE FROM habit_check_ins WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM habit_check_ins WHERE habitId = :habitId")
    suspend fun deleteByHabitId(habitId: String)

    @Query("DELETE FROM habit_check_ins")
    suspend fun clearAll()
}
