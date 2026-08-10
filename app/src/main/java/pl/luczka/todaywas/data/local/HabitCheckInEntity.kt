package pl.luczka.todaywas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_check_ins",
    indices = [Index(value = ["habitId", "date"], unique = true)],
)
data class HabitCheckInEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: String,
    val value: Int,
    val createdAt: Long,
)
