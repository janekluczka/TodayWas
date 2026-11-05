package pl.luczka.todaywas.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pl.luczka.todaywas.core.database.entity.UserPreferencesEntity

@Dao
interface UserPreferencesDao {

    @Query("SELECT * FROM user_preferences WHERE id = 0")
    fun observe(): Flow<UserPreferencesEntity?>

    @Upsert
    suspend fun upsert(entity: UserPreferencesEntity)
}
