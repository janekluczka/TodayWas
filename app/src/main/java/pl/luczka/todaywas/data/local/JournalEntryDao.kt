package pl.luczka.todaywas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getById(id: Long): JournalEntryEntity?

    @Insert
    suspend fun insert(entity: JournalEntryEntity)

    @Update
    suspend fun update(entity: JournalEntryEntity)
}
