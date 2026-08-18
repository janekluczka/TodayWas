package pl.luczka.todaywas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// No @Index(unique) here: uniqueness among active (non-deleted) rows is enforced via a raw partial
// index created in TodayWasDatabaseCallbacks.kt instead - Room's annotation can't express a WHERE
// clause, and a plain unique index would collide with a soft-deleted row occupying the same slot.
@Entity(tableName = "habit_check_ins")
data class HabitCheckInEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: String,
    val value: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
