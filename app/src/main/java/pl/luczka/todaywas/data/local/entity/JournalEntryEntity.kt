package pl.luczka.todaywas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// No @Index(unique) here: uniqueness among active (non-deleted) rows is enforced via a raw partial
// index created in TodayWasDatabaseCallbacks.kt instead - Room's annotation can't express a WHERE
// clause, and a plain unique index would collide with a soft-deleted row occupying the same date.
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
