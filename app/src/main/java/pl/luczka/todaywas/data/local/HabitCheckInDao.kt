package pl.luczka.todaywas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitCheckInDao {

    @Query("SELECT * FROM habit_check_ins")
    fun observeAll(): Flow<List<HabitCheckInEntity>>

    @Query("SELECT * FROM habit_check_ins WHERE habitId = :habitId AND date = :date")
    suspend fun getByHabitAndDate(
        habitId: Long,
        date: String,
    ): HabitCheckInEntity?

    @Insert
    suspend fun insertOne(entity: HabitCheckInEntity)

    @Transaction
    suspend fun insertAll(entities: List<HabitCheckInEntity>) {
        entities.forEach { insertOne(it) }
    }

    @Update
    suspend fun update(entity: HabitCheckInEntity)
}
