package pl.luczka.todaywas.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 0")
    fun observe(): Flow<UserPreferencesEntity?>

    @Upsert
    suspend fun upsert(entity: UserPreferencesEntity)
}
