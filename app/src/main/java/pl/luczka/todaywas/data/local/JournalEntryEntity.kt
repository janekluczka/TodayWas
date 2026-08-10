package pl.luczka.todaywas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries", indices = [Index(value = ["date"], unique = true)])
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val text: String,
    val createdAt: Long,
)
