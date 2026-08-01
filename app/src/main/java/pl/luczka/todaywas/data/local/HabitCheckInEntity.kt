package pl.luczka.todaywas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_check_ins",
    indices = [Index(value = ["habitId", "date"], unique = true)],
)
data class HabitCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String,
    val value: Int,
    val createdAt: Long,
)
