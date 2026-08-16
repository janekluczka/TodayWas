package pl.luczka.todaywas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val type: String,
    val scaleMin: Int?,
    val scaleMax: Int?,
    val createdAt: Long,
)
