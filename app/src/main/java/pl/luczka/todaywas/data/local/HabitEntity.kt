package pl.luczka.todaywas.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val type: String,
    val scaleMin: Int?,
    val scaleMax: Int?,
    val createdAt: Long,
)
