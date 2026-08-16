package pl.luczka.todaywas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.data.local.entity.UserPreferencesEntity

@Dao
interface UserPreferencesDao {

    @Query("SELECT * FROM user_preferences WHERE id = 0")
    fun observe(): Flow<UserPreferencesEntity?>

    @Upsert
    suspend fun upsert(entity: UserPreferencesEntity)
}
