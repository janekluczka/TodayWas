package pl.luczka.todaywas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Insert
    suspend fun insert(entity: HabitEntity)
}
